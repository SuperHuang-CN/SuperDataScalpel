# Canvas `VALUE_MAPPING` Processor 设计文档

## 1. 状态、目标与范围

- 设计状态：已实现。
- 正式协议版本：Canvas `1.15`。
- 节点类型：`VALUE_MAPPING`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

`VALUE_MAPPING` 用于把字段中的少量、明确原值映射为标准值，例如状态码统一、行政编码修正和历史枚举兼容。节点只处理精确、静态、内联的小型字典：

- 一个节点可以配置多个字段。
- 每个字段有独立映射表和未匹配策略。
- 字段类型保持不变，不在映射节点中进行隐式类型转换。
- NULL 不参与普通值匹配，始终保留为 NULL；NULL 补值使用 `NULL_HANDLING`。

大型或频繁维护的字典不得内联到 Canvas Definition，应建成 JDBC、模型或文件数据集 Input，并通过 `JOIN` 处理。

## 2. 稳定配置协议

Java 稳定定义只能位于 `data-scalpel-contracts`。Business、Task Engine 和 Manifest 直接使用同一 Contracts 模型；前端以强类型判别联合镜像编辑契约。

```ts
interface ValueMappingConfiguration {
  sourceTableName: string;
  outputTableName: string;
  rules: ValueMappingRule[];
}

interface ValueMappingRule {
  columnName: string;
  entries: ValueMappingEntry[];
  unmatchedStrategy: 'KEEP' | 'SET_NULL' | 'SET_LITERAL' | 'ERROR';
  unmatchedValue: CanvasLiteral | null;
}

interface ValueMappingEntry {
  sourceValue: CanvasLiteral;
  targetValue: CanvasLiteral | null;
}
```

示例：

```json
{
  "sourceTableName": "customers",
  "outputTableName": "standardized_customers",
  "rules": [
    {
      "columnName": "status",
      "entries": [
        {
          "sourceValue": {
            "dataType": "STRING",
            "value": "1"
          },
          "targetValue": {
            "dataType": "STRING",
            "value": "ACTIVE"
          }
        },
        {
          "sourceValue": {
            "dataType": "STRING",
            "value": "0"
          },
          "targetValue": {
            "dataType": "STRING",
            "value": "INACTIVE"
          }
        }
      ],
      "unmatchedStrategy": "ERROR",
      "unmatchedValue": null
    },
    {
      "columnName": "customer_level",
      "entries": [
        {
          "sourceValue": {
            "dataType": "STRING",
            "value": "vip"
          },
          "targetValue": {
            "dataType": "STRING",
            "value": "VIP"
          }
        }
      ],
      "unmatchedStrategy": "KEEP",
      "unmatchedValue": null
    }
  ]
}
```

协议规则：

- `sourceTableName`、`outputTableName` 必填。
- `rules` 至少一项，最多 100 项；同一字段只能配置一条规则。
- 每条规则至少一个映射项，最多 200 项；单节点映射项总数最多 2,000。
- `sourceValue` 必须是非 NULL 的稳定 Literal。
- 同一字段规则内的 `sourceValue` 不得重复；重复判断不能只比较原始字符串，必须先通过共享 Literal 解析器转换为类型化值，再按平台类型建立规范键。
- 数值按实际数值比较，例如 DECIMAL `1.0` 与 `1.00`、浮点 `-0.0` 与 `0.0` 视为同一来源值；TIMESTAMP 按同一瞬时时间比较，BINARY 按 Base64 解码后的字节比较，STRING 保持大小写敏感的精确比较。
- FLOAT 和 DOUBLE Literal 必须是有限值；`NaN`、正无穷和负无穷不进入映射协议。
- `targetValue=null` 明确表示输出 SQL NULL。
- 非 NULL 的 `sourceValue`、`targetValue` 和 `unmatchedValue` 的平台类型必须与目标字段 `PlatformDataType` 一致。
- 映射不改变字段类型。需要类型变化时，应先或后使用 `TYPE_CAST`。
- GEOMETRY 不支持内联值映射；空间对象处理使用专用空间节点。
- 映射项数组顺序为稳定 UI 顺序，但由于源值必须唯一，不影响匹配结果。

未匹配策略：

| 策略 | 非 NULL 值未命中映射时的行为 | `unmatchedValue` |
| --- | --- | --- |
| `KEEP` | 保留原值 | 必须为 null |
| `SET_NULL` | 输出 NULL | 必须为 null |
| `SET_LITERAL` | 输出配置的固定值 | 必须非 null |
| `ERROR` | Runner 以稳定业务错误终止节点 | 必须为 null |

来源字段原值为 NULL 时不视为“未匹配”，无论选择哪种策略都保持 NULL。

## 3. 执行语义

Operator 对每条字段规则基于原始来源 Dataset 构造一个 Spark CASE 表达式：

1. 首先保留原始 NULL。
2. 按映射项生成精确等值匹配分支。
3. 命中后返回目标 Literal 或 typed NULL。
4. 未命中时执行 `unmatchedStrategy`。
5. 最终结果显式保持目标字段原 Spark 类型，并继续使用原字段名。
6. 全部受影响字段和未修改字段通过一次最终 `select` 输出，保持原字段顺序。

`ERROR` 使用不包含实际数据值的安全失败表达式。Compiler 可建立该表达式的零行计划，但不会读取真实数据，因此配置合法时预检仍可通过；Runner 遇到第一条未匹配非 NULL 数据时返回 `VALUE_MAPPING_UNMATCHED_VALUE`。

不得使用 UDF、用户 SQL、驱动端 Map 查找、`collect` 或广播外部数据。映射超过协议上限时应提示改用 Input + Join，而不是在实现中自动切换另一套执行方式。

## 4. Map、Schema 与批流传播

Map 规则：

- 保留输入 Map 中全部表。
- 以 `outputTableName` 追加映射结果。
- 输出名与任何现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 不修改来源 Map、Schema 或 Dataset。

Schema 规则：

- 字段名称、顺序和类型保持不变。
- 未映射字段完整继承来源元数据。
- 被映射字段保留长度、精度、Scale 和注释；default、autoIncrement、generated 等物理写入属性清空。
- 选择 `SET_NULL` 或任一 `targetValue=null` 时，目标字段 nullable 固定为 true；其他情况取 Spark Analyzer 结果。
- `origin`、`datasetKind`、`eventTimeColumn` 和 `watermarkDelay` 默认继承来源表。

流式约束：

- 节点是确定的逐行无状态变换，可同时用于有界和无界表。
- 流模式不得映射当前 `eventTimeColumn`，否则返回 `STREAM_EVENT_TIME_COLUMN_IMMUTABLE`。
- 节点不携带 Watermark、状态 TTL、Checkpoint 或输出模式配置。

## 5. 校验与稳定错误码

复用：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`
- `STREAM_EVENT_TIME_COLUMN_IMMUTABLE`

新增：

| 错误码 | 条件 |
| --- | --- |
| `EMPTY_VALUE_MAPPING_RULES` | `rules` 为空 |
| `VALUE_MAPPING_RULE_LIMIT_EXCEEDED` | 字段规则超过 100 项 |
| `DUPLICATE_VALUE_MAPPING_COLUMN` | 同一字段配置多条规则 |
| `EMPTY_VALUE_MAPPING_ENTRIES` | 某字段没有映射项 |
| `VALUE_MAPPING_ENTRY_LIMIT_EXCEEDED` | 单字段超过 200 项或节点总数超过 2,000 |
| `DUPLICATE_VALUE_MAPPING_SOURCE` | 同一字段的规范化源值重复 |
| `VALUE_MAPPING_SOURCE_REQUIRED` | sourceValue 缺失或为 NULL |
| `INVALID_VALUE_MAPPING_LITERAL` | Literal 类型或稳定字符串格式非法 |
| `VALUE_MAPPING_LITERAL_TYPE_MISMATCH` | Literal 平台类型与字段不一致 |
| `VALUE_MAPPING_TYPE_NOT_SUPPORTED` | 字段类型不支持内联映射 |
| `INVALID_UNMATCHED_VALUE_STRATEGY` | unmatchedStrategy 未知 |
| `UNMATCHED_VALUE_REQUIRED` | SET_LITERAL 没有 unmatchedValue |
| `UNMATCHED_VALUE_NOT_ALLOWED` | 非 SET_LITERAL 携带 unmatchedValue |
| `VALUE_MAPPING_UNMATCHED_VALUE` | Runner 遇到 ERROR 策略下的未匹配非 NULL 值 |

`VALUE_MAPPING_UNMATCHED_VALUE` 属于运行时确定的节点业务失败，错误详情只能指出字段名和规则，不得回显实际未匹配值。Compiler 不扫描数据验证映射覆盖率。

## 6. 前端设计器

- Palette 名称为“值映射”，说明为“把少量原值精确映射为标准值”。
- 批处理和实时模式均展示。
- 使用独立 `ValueMappingProcessorInspector`。
- 顶部配置来源表和输出表；下方按字段展示可折叠规则卡片。
- 每个字段规则包含：
  - 字段选择和字段平台类型。
  - 原值/目标值紧凑表格。
  - 未匹配策略。
  - SET_LITERAL 时出现的默认值输入。
- 目标值提供“映射为 NULL”开关，不使用字符串 `"null"` 代替 SQL NULL。
- Inspector 明确提示“来源 NULL 始终保持 NULL；补值请使用空值处理”。
- 提供多行粘贴辅助时，只作为编辑器一次性转换：预览并确认后写入明确 entries，不把 CSV、TSV 原文或文件引用保存进定义。
- 超过 200 条时提示“请将字典建成数据集并使用 Join”，不允许绕过协议上限。
- 上游字段失效时保留字段名、映射项和策略并标红。
- 流模式选择事件时间字段时持续显示禁止原因。
- 节点摘要显示来源/输出表、字段规则数、映射项总数和未匹配策略集合，不显示任何源值、目标值或默认值。

## 7. Task Engine、日志与失败边界

新增唯一无状态 `ValueMappingNodeOperator`：

- `category()` 返回 `PROCESSOR`。
- `supportedModes()` 返回 `BATCH`、`STREAMING`。
- Compiler 与 Runner 通过同一内置 Registry 调用。
- 复用共享 Canvas Literal 解析能力。
- 使用 Spark Column API 构造 CASE 表达式，不拼接 SQL。
- 使用一次 `select + alias` 保持字段顺序和稳定名称。
- 成功后复制输入 Map 并追加输出表。
- `VALUE_MAPPING_UNMATCHED_VALUE` 分类为不可重试的 `CONSTRAINT`，且不携带实际值。
- 其他未分类运行时失败回退为现有 `PROCESSOR_EXECUTION_FAILED`。
- 成功节点结果消息固定为“值映射已准备”。

节点生命周期阶段为 `PROCESS`。安全摘要只记录：

- 来源表和输出表。
- 被映射字段名。
- 每个字段的映射项数量。
- 未匹配策略。
- 是否存在映射为 NULL 的项。

日志、错误、编译响应和节点结果不得记录 sourceValue、targetValue、unmatchedValue 或实际未匹配数据。`ERROR` 策略运行失败复用统一诊断 ID 和节点失败包装。

## 8. 实施记录

本节点已按以下范围完成全链路接入：

1. `data-scalpel-contracts` 已增加配置、规则、映射项和未匹配策略，并注册 `VALUE_MAPPING`。
2. `VALUE_MAPPING` 的最低协议版本固定为 Canvas `1.15`；低版本携带该节点返回 `NODE_TYPE_REQUIRES_SCHEMA_VERSION`。
3. Business Validator、Upgrader、持久化和 Manifest 直接使用 Contracts 类型，没有复制映射模型或整棵转换代码。
4. Task Engine 已注册唯一 `ValueMappingNodeOperator`，并接入图规则、批流模式、Schema 传播、安全摘要和运行时业务错误分类。
5. 前端已同步类型、默认草稿、JSON IO、Registry、Ports、Palette、序列化和独立 Inspector。
6. 正式配置、传播语义和错误边界已并入 `canvas-task-definition.md`。

## 9. 行为验收标准

- KEEP、SET_NULL、SET_LITERAL 和 ERROR 四种策略可以稳定 JSON 往返。
- 多字段映射通过一次最终投影保持来源字段顺序和类型。
- 源值重复、字段重复、非法 Literal、错误 unmatchedValue 组合和容量超限返回准确路径错误。
- 来源 NULL 始终保持 NULL，不触发 ERROR，也不应用 unmatchedValue。
- SET_NULL 和映射为 NULL 能正确传播 nullable。
- ERROR 在 Compiler 零行预检时可建立计划，在 Runner 命中未映射数据时返回安全稳定错误。
- 批流使用同一配置和 Operator；流模式禁止映射事件时间字段。
- 日志、错误和节点摘要不泄漏映射内容或实际数据值。

## 10. 测试设计

- Contracts/Jackson：四种未匹配策略、nullable 目标值、数组顺序、`1.15` 版本门槛和旧定义升级。
- 结构校验：字段重复、源值重复、错误 Literal 类型、unmatchedValue 组合和容量上限。
- Spark Operator：KEEP、SET_NULL、SET_LITERAL、映射为 NULL、多字段单次投影和 Schema nullable。
- 运行时 ERROR：匹配数据成功，未匹配非 NULL 值产生安全 `VALUE_MAPPING_UNMATCHED_VALUE`，来源 NULL 不失败。
- Map/批流传播：保留全部输入表、追加输出表、有界性继承和事件时间不可修改。
- Compiler：零行 Dataset 可分析 ERROR 分支，不读取真实数据或检查覆盖率。
- 前端：映射项编辑、多行粘贴确认、策略切换、上游失效保留、容量提示和 JSON 往返。
- 日志安全：所有摘要、异常和节点结果均不出现源值、目标值、默认值或实际未匹配值。

## 11. 不在范围内

- 范围映射、模糊匹配、正则匹配、大小写不敏感匹配和 Locale 规则。
- NULL 原值映射；使用 `NULL_HANDLING`。
- 字段类型转换；使用 `TYPE_CAST`。
- 远程字典、动态字典、字典版本和缓存。
- 超过协议容量的大型字典；使用 Input + Join。
- 一条规则同时改字段名；改名使用 `RENAME`。

## 12. 协议兼容

Canvas `1.15` 已正式引入 `VALUE_MAPPING`，并以前序 `NULL_HANDLING@1.14` 为兼容基线。当前协议版本为 `1.22`，兼容读取 `1.0`～`1.22` 并统一规范化为 `1.22`；`1.0`～`1.14` 定义不得携带本节点。

未来需要范围、正则或远程字典时，应新增语义明确的节点或复用 Join，不能向 `ValueMappingEntry` 塞入可选操作符、SQL 或资源引用，使精确映射协议退化成通用规则引擎。
