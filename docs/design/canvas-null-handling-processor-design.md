# Canvas `NULL_HANDLING` Processor 设计文档

## 1. 状态、目标与范围

- 设计状态：已实现。
- 正式协议版本：Canvas `1.14`。
- 节点类型：`NULL_HANDLING`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：恰好一条入边，至少一条出边。

`NULL_HANDLING` 用于对一张逻辑表执行常见空值处理，并以新的逻辑表名追加结果。首期只提供两类边界清晰的规则：

- 根据一个或多个字段的 NULL 状态删除整行。
- 使用明确的非 NULL Literal 填充某个字段的 NULL。

规则按配置顺序执行，因此“先填充再删除”和“先删除再填充”可以表达不同业务语义。首期不提供字段间填充；使用另一字段补值时，应使用 `DERIVE_COLUMNS` 的 `COALESCE` 并显式覆盖目标字段，避免在本节点中引入隐式类型转换。

## 2. 稳定配置协议

Java 稳定定义只能放在 `data-scalpel-contracts`。Business、Task Engine 和 Manifest 直接使用同一组 Contracts 类型，不得复制第二套节点、规则或枚举模型。前端以明确的 TypeScript 判别联合镜像编辑契约。

```ts
interface NullHandlingConfiguration {
  sourceTableName: string;
  outputTableName: string;
  rules: NullHandlingRule[];
}

type NullHandlingRule =
  | DropNullRowsRule
  | FillNullLiteralRule;

interface DropNullRowsRule {
  kind: 'DROP_ROW';
  columnNames: string[];
  matchMode: 'ANY_NULL' | 'ALL_NULL';
}

interface FillNullLiteralRule {
  kind: 'FILL_LITERAL';
  columnName: string;
  value: CanvasLiteral;
}
```

示例：

```json
{
  "sourceTableName": "orders",
  "outputTableName": "orders_without_nulls",
  "rules": [
    {
      "kind": "FILL_LITERAL",
      "columnName": "discount_amount",
      "value": {
        "dataType": "DECIMAL",
        "value": "0.00"
      }
    },
    {
      "kind": "DROP_ROW",
      "columnNames": [
        "order_id",
        "customer_id"
      ],
      "matchMode": "ANY_NULL"
    }
  ]
}
```

协议规则：

- `sourceTableName`、`outputTableName` 必填。
- `rules` 按数组顺序执行，至少一项，最多 100 项。
- 规则使用 `kind` 作为稳定判别字段，不保存 UI 组件类型、Spark 表达式或 SQL。
- `DROP_ROW.columnNames` 至少一项，同一规则中字段不得重复。
- `ANY_NULL` 表示任一选中字段为 NULL 就删除该行。
- `ALL_NULL` 表示所有选中字段都为 NULL 才删除该行。
- `FILL_LITERAL` 只在目标字段当前值为 NULL 时填充；非 NULL 原值保持不变。
- 同一节点内一个字段最多出现一次 `FILL_LITERAL`，避免后续永远无法生效的冗余填充。
- `FILL_LITERAL.value` 必须是非 NULL 的稳定 `CanvasLiteral`。
- Literal 的 `dataType` 必须与目标字段的 `PlatformDataType` 一致；DECIMAL 精度和 Scale 等实际目标参数来自来源字段 Schema。
- Literal 字符串格式复用现有 Canvas Literal 规则：Boolean 使用 `true/false`，日期使用 `yyyy-MM-dd`，Timestamp 使用 ISO-8601，Binary 使用 Base64。
- 首期不支持 GEOMETRY Literal。需要空间对象补值时应使用未来专用空间 Processor。

## 3. 执行语义

Operator 从合并后的输入 Map 中按 `sourceTableName` 精确查找来源表，并在局部变量中依次应用规则：

1. `DROP_ROW + ANY_NULL` 使用选中字段 `isNull` 条件的 OR，并保留其否定条件成立的行。
2. `DROP_ROW + ALL_NULL` 使用选中字段 `isNull` 条件的 AND，并保留其否定条件成立的行。
3. `FILL_LITERAL` 将 Literal 转换为目标字段的精确 Spark 类型，并使用 `coalesce(targetColumn, typedLiteral)` 保持非 NULL 原值。
4. 每条规则读取上一条规则形成的 Dataset，不回读原始 Dataset。
5. 全部规则完成后分析最终计划并生成输出 Schema。

`DROP_ROW` 必须显式使用 `isNull`，不能直接调用会把部分 `NaN` 一并视为空值的便捷 API；浮点 `NaN` 不属于本节点定义的 NULL，首期保持原值。

Compiler 使用元数据 Schema 构造的零行 Dataset 调用同一个 Operator，不读取真实数据，不统计被删除或填充的行数。Runner 执行同一逻辑，也不得为了节点日志额外调用 `count`、`collect` 或其他 Spark Action。

## 4. Map、Schema 与批流传播

Map 规则：

- 保留输入 Map 中所有表。
- 以 `outputTableName` 追加处理结果。
- 输出名与任一现有 Map Key 冲突时返回 `DUPLICATE_TABLE_NAME`，包括来源表名。
- 不修改输入 Map、上游 `SparkCanvasTable`、Dataset 或 Schema。

Schema 规则：

- 字段名称、顺序和类型保持不变。
- 未被填充的字段完整继承来源字段元数据。
- 被 `FILL_LITERAL` 修改的字段保留长度、精度、Scale 和注释；nullable 取 Spark Analyzer 最终结果。
- 被填充字段的 default、autoIncrement、generated 等物理写入属性清空，因为结果值已经不是原物理字段的直接投影。
- 只有 `DROP_ROW` 规则时，字段元数据完整继承来源表；不根据过滤条件自行把 nullable 改成 false。
- `origin` 继承来源表。
- `datasetKind`、`eventTimeColumn` 和 `watermarkDelay` 默认继承来源表。

流式约束：

- `DROP_ROW` 可以检查事件时间字段，删除事件时间为空的行不会修改非空事件时间值。
- `FILL_LITERAL` 不得修改当前 `eventTimeColumn`，否则返回 `STREAM_EVENT_TIME_COLUMN_IMMUTABLE`。
- 节点无状态，不增加 Watermark、状态 TTL、输出模式或 Checkpoint 配置。

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
| `EMPTY_NULL_HANDLING_RULES` | `rules` 为空 |
| `NULL_HANDLING_RULE_LIMIT_EXCEEDED` | 规则超过 100 项 |
| `INVALID_NULL_HANDLING_RULE_KIND` | `kind` 未知 |
| `EMPTY_NULL_CHECK_COLUMNS` | `DROP_ROW.columnNames` 为空 |
| `DUPLICATE_NULL_CHECK_COLUMN` | 同一 DROP_ROW 规则中字段重复 |
| `INVALID_NULL_MATCH_MODE` | `matchMode` 未知 |
| `DUPLICATE_NULL_FILL_COLUMN` | 同一字段配置多个 FILL_LITERAL |
| `NULL_FILL_VALUE_REQUIRED` | 填充值缺失或为 NULL |
| `INVALID_NULL_FILL_LITERAL` | Literal 类型或稳定字符串格式非法 |
| `NULL_FILL_LITERAL_TYPE_MISMATCH` | Literal 平台类型与目标字段不一致 |
| `NULL_FILL_LITERAL_TYPE_NOT_SUPPORTED` | 首期不支持的 Literal 类型 |

配置缺失时仍应保留 Compiler 已安全取得的 `inputTables`。字段类型和值格式属于确定的协议校验；Spark 表达式能否建立计划由 Analyzer 决定。Compiler 不根据真实数据产生“预计填充多少行”之类的诊断。

## 6. 前端设计器

- Palette 名称为“空值处理”，说明为“删除含空值的行，或使用固定值填充空字段”。
- 批处理和实时模式均展示该节点。
- 使用独立 `NullHandlingProcessorInspector`，不继续扩大通用 Inspector。
- 顶部配置来源表和输出表；下方使用可排序规则列表明确展示执行序号。
- 添加规则时先选择“删除行”或“固定值填充”：
  - 删除行：多选字段，选择“任一为空”或“全部为空”。
  - 固定值填充：单选目标字段，根据字段平台类型展示紧凑输入控件。
- 规则拖动排序后，应持续显示“规则按从上到下执行”的提示。
- 选择流式事件时间字段进行填充时立即显示禁止原因；删除事件时间为空的行仍允许。
- 上游字段消失时保留原字段名和 Literal，并在原位置标红，不静默清空或改名。
- Compiler 不可用时显示等待或失败状态，不把空响应解释为没有字段。
- 节点摘要只显示来源表、输出表、删除规则数和填充规则数，不显示 Literal 实际值。

## 7. Task Engine、日志与失败边界

新增唯一无状态 `NullHandlingNodeOperator`：

- `category()` 返回 `PROCESSOR`。
- `supportedModes()` 返回 `BATCH`、`STREAMING`。
- Compiler 与 Runner 都通过内置 Registry 调用该 Operator。
- 规则字段使用安全标识符引用，Literal 使用共享解析器构造，不拼接用户 SQL。
- 成功后复制输入 Map 并追加输出表。
- Analyzer 失败返回 `SPARK_ANALYSIS_ERROR`。
- 未分类运行时失败回退为现有 `PROCESSOR_EXECUTION_FAILED`。
- 成功节点结果消息固定为“空值处理已准备”。

节点生命周期阶段为 `PROCESS`。安全摘要只允许记录：

- 来源表和输出表。
- 规则数量及规则种类。
- DROP_ROW 字段名与匹配模式。
- FILL_LITERAL 目标字段名和 Literal 平台类型。

安全摘要、日志、节点结果和异常消息不得记录填充值、被处理的数据行或统计样本。真实运行时异常继续由统一错误分类器生成诊断 ID。

## 8. 实施记录

本节点已按以下范围完成全链路接入：

1. `data-scalpel-contracts` 已增加节点定义、配置、规则联合和枚举，并将 `NULL_HANDLING` 加入稳定节点联合。
2. `NULL_HANDLING` 的最低协议版本固定为 Canvas `1.14`；低版本携带该节点返回 `NODE_TYPE_REQUIRES_SCHEMA_VERSION`。
3. Business Validator、Upgrader、持久化和 Manifest 直接使用 Contracts 类型，没有建立 Business 内部副本。
4. Task Engine 已注册唯一 `NullHandlingNodeOperator`，并接入模式、图度数、Schema 传播、安全摘要和失败分类。
5. 前端已同步判别联合、默认草稿、JSON IO、Registry、Ports、Palette、序列化和独立 Inspector。
6. 正式配置、传播语义和错误边界已并入 `canvas-task-definition.md`。

## 9. 行为验收标准

- ANY_NULL、ALL_NULL 和固定值填充定义可稳定 JSON 往返。
- 规则顺序改变时生成的 Spark 计划语义同步改变。
- 空规则、重复字段、重复填充目标、无效 Literal 和表名冲突返回稳定路径错误。
- 输出字段顺序和类型不变，nullable 与被修改字段元数据按设计传播。
- 批流使用同一配置和同一个 Operator。
- 流模式允许删除空事件时间行，但拒绝填充事件时间字段。
- Compiler 保留可用 `inputTables`，不读取真实数据或触发 Spark Action。
- 日志和编译结果不包含填充值或实际数据。

## 10. 测试设计

- Contracts/Jackson：两种规则判别、顺序保持、`1.14` 版本门槛和旧定义升级。
- 结构校验：空规则、规则上限、空字段、重复字段、重复填充目标和非法 Literal。
- Spark Operator：ANY_NULL、ALL_NULL、先填充后删除、先删除后填充及输出 Schema。
- Map 传播：保留全部输入表、追加输出表和同名 Key 冲突。
- 批流语义：有界性继承、流式事件时间字段仅允许 DROP_ROW。
- Compiler：未完成配置和模式错误仍保留安全 `inputTables`，零行预检不触发 Action。
- 前端：规则增删排序、类型化 Literal、失效字段保留、节点摘要和 JSON 往返。
- 日志安全：摘要、异常和节点结果中不出现填充值或数据行。

## 11. 不在范围内

- 使用另一字段、表达式、模型或远程字典填充；字段间补值使用 `DERIVE_COLUMNS`。
- 前向填充、后向填充、均值/中位数填充；这些操作需要排序或聚合语义。
- 空字符串、零值、特殊编码自动视为 NULL。
- 输出被删除行、质量报告或处理行数统计。
- GEOMETRY Literal、SQL 片段、脚本和 UDF。

## 12. 协议兼容

Canvas `1.14` 已正式引入 `NULL_HANDLING`。当前协议版本为 `1.22`，兼容读取 `1.0`～`1.22` 并统一规范化为 `1.22`；`1.0`～`1.13` 定义不得携带本节点。

后续增加字段间填充或统计填充时，应优先复用 `DERIVE_COLUMNS`、`AGGREGATE` 和 `JOIN`；只有出现无法通过现有节点清晰表达的稳定语义，才扩展本节点协议。
