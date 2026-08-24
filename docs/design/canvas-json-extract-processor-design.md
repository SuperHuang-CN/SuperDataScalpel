# Canvas `JSON_EXTRACT` Processor 设计文档

## 1. 状态、目标与范围

- 设计状态：已实现。
- 正式协议版本：Canvas `1.19`。
- 节点类型：`JSON_EXTRACT`。
- 节点类别：`PROCESSOR`。
- 执行模式：`BATCH`、`STREAMING`。
- 图规则：至少一条入边和一条出边；多个上游表 Map 先执行无覆盖合并。

`JSON_EXTRACT` 从一张逻辑表的一个 STRING 字段中按 JSON Path 提取多个结构化字段，并以新的逻辑表名追加结果。该节点用于日志载荷、API 原始响应、Kafka JSON 文本等已经以字符串进入 Canvas 的场景。

首期只处理扁平输出字段。一个提取项生成一个平台标量字段，不生成 STRUCT、ARRAY、MAP 或 GEOMETRY，不提供自由 SQL、JavaScript、UDF、JSON 修复或 Schema 自动推断。

## 2. 稳定配置协议

Java 稳定定义位于 `data-scalpel-contracts`，Business、Task Engine 和 Manifest 直接使用同一组类型。前端使用明确的 TypeScript 判别联合镜像编辑契约。

```ts
interface JsonExtractConfiguration {
  sourceTableName: string;
  outputTableName: string;
  sourceColumnName: string;
  extractions: JsonExtraction[];
  failureStrategy: 'ERROR' | 'SET_NULL';
}

interface JsonExtraction {
  jsonPath: string;
  outputColumnName: string;
  targetType: PlatformTypeDefinition;
}
```

示例：

```json
{
  "sourceTableName": "orders",
  "outputTableName": "orders_json_extracted",
  "sourceColumnName": "payload",
  "extractions": [
    {
      "jsonPath": "$.customer.id",
      "outputColumnName": "customer_id",
      "targetType": {
        "type": "STRING",
        "length": 64,
        "precision": null,
        "scale": null,
        "geometry": null
      }
    },
    {
      "jsonPath": "$.amount",
      "outputColumnName": "payload_amount",
      "targetType": {
        "type": "DECIMAL",
        "length": null,
        "precision": 18,
        "scale": 2,
        "geometry": null
      }
    }
  ],
  "failureStrategy": "SET_NULL"
}
```

协议限制：

- `sourceTableName`、`outputTableName`、`sourceColumnName` 必填。
- 来源字段必须为平台 `STRING`。
- `extractions` 至少一项、最多 100 项。
- `jsonPath` 长度不超过 512，必须以 `$` 开头，完整语法由 Spark Analyzer 校验。
- `outputColumnName` 不能与来源字段或其他提取项重名。
- `targetType` 支持 Canvas 当前平台标量类型，首期不支持 `GEOMETRY`。
- STRING length 和 DECIMAL precision/scale 遵循 `PlatformTypeDefinition` 约束。
- 配置数组顺序稳定保存，并决定新增字段顺序。

## 3. 解析与失败语义

Operator 使用 Spark VARIANT API，不拼接用户 SQL：

- `ERROR`：`parse_json` 解析 JSON，`variant_get` 按 Path 取值并转换为目标 Spark SQL 类型。
- `SET_NULL`：`try_parse_json` 解析 JSON，`try_variant_get` 按 Path 取值并尝试转换。

统一语义：

- 来源 JSON 为 SQL NULL 时，所有提取字段为 NULL。
- JSON Path 不存在时，所有策略都返回 NULL。
- JSON Path 命中 JSON null 时返回 SQL NULL。
- `ERROR` 遇到畸形 JSON 或无法转换到目标类型的实际值时运行失败。
- `SET_NULL` 遇到畸形 JSON 或转换失败时返回 NULL，不终止节点。
- Compiler 只分析零行计划，不能证明真实 JSON 内容一定合法，也不能统计 Path 命中率或失败值数量。

Spark 采用惰性执行，真实数据错误可能在下游 Action 触发时出现。未分类失败统一回退为 `PROCESSOR_EXECUTION_FAILED`，不根据异常文本泄露 JSON 内容，也不虚假承诺能稳定归因到某个 Path。

## 4. Map、Schema 与批流传播

Map 规则：

- 从输入 Map 按 `sourceTableName` 精确查找来源表。
- 保留输入 Map 中所有表。
- 以 `outputTableName` 追加提取结果。
- 输出名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`。
- 不修改输入 Map、来源 Dataset 或来源 Schema。

Schema 规则：

- 来源字段完整保留，名称、顺序和平台元数据不变。
- 新字段按 `extractions` 数组顺序追加。
- 新字段类型取 `targetType`，固定为 nullable。
- 新字段的 default、autoIncrement、generated 和 comment 清空。
- 输出 `origin`、`datasetKind`、`eventTimeColumn` 和 `watermarkDelay` 继承来源表。
- 节点只读取 JSON 来源字段，不覆盖事件时间字段，因此批流共用同一配置和 Operator。

STRING length 属于平台 Schema 元数据，不表示 Spark 会在 JSON 提取时自动截断实际字符串。目标 Sink 的长度风险继续由 Output 节点按现有策略分析。

## 5. 校验与稳定错误码

复用：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `INVALID_TARGET_PLATFORM_TYPE`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

新增：

| 错误码 | 条件 |
| --- | --- |
| `EMPTY_JSON_EXTRACTIONS` | 没有配置提取项 |
| `JSON_EXTRACTION_LIMIT_EXCEEDED` | 提取项超过 100 |
| `JSON_SOURCE_COLUMN_TYPE_MISMATCH` | 来源字段不是 STRING |
| `INVALID_JSON_PATH` | Path 超过 512 或未以 `$` 开头 |
| `DUPLICATE_JSON_OUTPUT_COLUMN` | 输出字段与来源字段或其他提取项重名 |
| `INVALID_JSON_FAILURE_STRATEGY` | 失败策略为空或未知 |

配置缺失或节点预置错误时，Compiler 仍应保留已经安全合并的 `inputTables`，供 Inspector 继续选择来源表和字段。只有上游本身无法传播时才停止当前节点输出。

## 6. 前端设计器

- Palette 名称为“JSON 提取”，说明为“从 JSON 字符串按 Path 提取结构化字段”。
- 节点位于 Processor 的“字段处理”分组，批处理和实时模式均展示。
- 使用独立 `JsonExtractProcessorInspector` 和 `nodes/jsonExtract` Spec，不扩大统一 Inspector 的节点类型分支。
- 来源表和字段候选只使用 Task Engine 返回的 `inputTables`；来源字段只列出 STRING。
- 来源表、来源字段变更或字段类型变化时保留原配置，并在原位置显示失效原因。
- 提取项支持新增、上移、下移和删除。
- 每项编辑 JSON Path、输出字段名、目标平台类型以及 STRING/DECIMAL 参数。
- `SET_NULL` 显示明确警告，说明畸形 JSON 和转换失败会产生 NULL。
- 节点摘要显示来源表/字段、输出表、提取数量和失败策略，不显示 Path。
- JSON 导入严格验证协议结构、容量和平台类型；字段不存在、重名等业务无效配置继续交给 Task Engine 展示。

## 7. Task Engine、日志与安全边界

唯一无状态 `JsonExtractNodeOperator`：

- `category()` 返回 `PROCESSOR`。
- `supportedModes()` 返回 `BATCH`、`STREAMING`。
- Compiler 与 Runner 通过同一内置 Registry 调用同一 Operator。
- 使用 `parse_json/try_parse_json` 和 `variant_get/try_variant_get` 构造安全 Column 表达式。
- 通过一次最终投影保留来源字段并追加提取字段。
- 调用 Spark Schema 分析验证 Path 和目标类型计划，但不触发 Action。
- 成功节点结果消息固定为“JSON 提取已准备”。
- 节点生命周期阶段为 `PROCESS`。

安全摘要只允许记录：

- 来源表和输出表。
- JSON 来源字段名。
- 提取项数量。
- 目标平台类型集合。
- 失败策略。

安全摘要、日志、异常、编译响应和节点结果不得记录 JSON Path、JSON 文本、样例值、失败值、实际提取结果或数据行。JSON Path 虽然不是凭据，也可能暴露业务载荷结构，因此按本节点的最小诊断原则排除。

## 8. 行为验收标准

- `ERROR` 和 `SET_NULL` 配置可稳定 JSON 往返。
- STRING、DECIMAL、日期时间、二进制和基础数值目标类型可形成 Spark 计划。
- 空提取项、容量超限、非 STRING 来源、无效 Path、重复输出字段和 GEOMETRY 目标返回稳定路径错误。
- 来源字段顺序和元数据不变，新字段按配置顺序追加且 nullable 为 true。
- 输入 Map 完整保留，输出表只追加不覆盖。
- 批流使用同一 Operator，并继承来源有界性、事件时间和 Watermark。
- Compiler 不读取真实 JSON、不触发 Action。
- 日志和安全摘要不包含 JSON Path 或数据值。

## 9. 不在范围内

- JSON Schema 推断、样例预览和 Path 自动补全。
- STRUCT、ARRAY、MAP、GEOMETRY 输出。
- JSON 数组展开；后续应设计独立 `EXPLODE` Processor。
- JSON 对象合并、修复、格式化或重新序列化。
- 自定义 SQL、JSONPath 脚本、JavaScript、Python 或 UDF。
- 失败行旁路输出、错误行数据集和提取质量统计。
- 根据真实数据自动选择目标类型。

## 10. 协议兼容

Canvas `1.19` 正式引入 `JSON_EXTRACT`。当前实现兼容读取 `1.0`～`1.22` 并统一规范化为 `1.22`；`1.0`～`1.18` 定义不得携带本节点。

未来增加数组展开或嵌套结构输出时，应优先新增语义独立的 Processor，不在本节点中加入会改变行数或产生复杂嵌套 Schema 的可选字段。
