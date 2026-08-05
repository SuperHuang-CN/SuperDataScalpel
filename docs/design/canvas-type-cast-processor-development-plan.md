# Canvas `TYPE_CAST` Processor 开发计划

## 1. 目标与范围

新增 `TYPE_CAST` Processor，对一张上游逻辑表中的一个或多个字段执行显式平台类型转换，并以新的逻辑表名输出。

首期目标：

- 使用稳定 `PlatformTypeDefinition` 表达目标类型。
- 支持失败即终止和失败值置空两种策略。
- 同一配置支持 `BATCH`、`STREAMING`。
- 转换是否合法以实际 Spark Cast 表达式和 Analyzer 为准，不维护平台类型兼容矩阵。
- Compiler 与 Runner 使用唯一无状态 `TypeCastNodeOperator`。

节点类别为 `PROCESSOR`。图规则为恰好一条入边、至少一条出边。

## 2. 稳定配置协议

```ts
interface TypeCastConfiguration {
  sourceTableName: string;
  outputTableName: string;
  casts: ColumnTypeCast[];
}

interface ColumnTypeCast {
  columnName: string;
  targetType: PlatformTypeDefinition;
  failureStrategy: 'FAIL' | 'SET_NULL';
}
```

示例：

```json
{
  "sourceTableName": "orders",
  "outputTableName": "typed_orders",
  "casts": [
    {
      "columnName": "amount_text",
      "targetType": {
        "type": "DECIMAL",
        "precision": 18,
        "scale": 2
      },
      "failureStrategy": "FAIL"
    },
    {
      "columnName": "submitted_at_text",
      "targetType": {
        "type": "TIMESTAMP"
      },
      "failureStrategy": "SET_NULL"
    }
  ]
}
```

协议规则：

- `casts` 至少包含一项。
- 同一字段在一个节点内只能转换一次。
- `targetType` 直接复用 `data-scalpel-contracts` 的 `PlatformTypeDefinition`，不得写入 JDBC 类型名、Spark `DataType` JSON 或方言私有类型。
- `FAIL` 使用 ANSI Cast；真实值无法转换时节点运行失败。
- `SET_NULL` 使用 Spark 4.1.1 的 `try_cast` 语义；无法转换的真实值变为 null。
- 首期配置不暴露目标 nullable。`SET_NULL` 的输出字段固定为 nullable；若未来协议增加 nullable 声明，必须拒绝 `SET_NULL + non-null` 组合。

## 3. 输入、输出与 Schema 传播

Operator 精确查找 `sourceTableName`，基于来源 Dataset 构造一次 `select`：

- 未转换字段直接投影。
- 转换字段在原位置生成 cast/try_cast 表达式，并继续使用原字段名。

Map 规则：

- 保留输入 Map 中全部表。
- 以 `outputTableName` 新增转换结果。
- 输出名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`，不覆盖来源表。

Schema 规则：

- 未转换字段完整继承平台元数据。
- 转换字段的类型取 `targetType` 与 Analyzer 的最终结果。
- `FAIL` 的 nullable 由来源 nullable 与 Spark Analyzer 结果确定。
- `SET_NULL` 的 nullable 明确为 `true`。
- 转换字段清空不再可靠的 default、autoIncrement、generated 等物理写入属性。
- `origin`、`datasetKind` 继承来源表。
- `eventTimeColumn` 和 `watermarkDelay` 默认继承。
- `STREAMING` 模式禁止转换当前事件时间字段，避免在普通 Cast 节点中隐式改变事件时间及 Watermark 语义。

## 4. 校验与稳定错误码

复用：

- `CONFIGURATION_REQUIRED`
- `REQUIRED_CONFIGURATION`
- `TABLE_NOT_FOUND`
- `COLUMN_NOT_FOUND`
- `DUPLICATE_TABLE_NAME`
- `SPARK_ANALYSIS_ERROR`
- `NODE_EXECUTION_MODE_NOT_SUPPORTED`
- `UPSTREAM_INVALID`

新增：

| 错误码 | 条件 |
| --- | --- |
| `EMPTY_TYPE_CASTS` | `casts` 为空 |
| `DUPLICATE_CAST_COLUMN` | 同一字段重复配置转换 |
| `INVALID_TARGET_PLATFORM_TYPE` | `PlatformTypeDefinition` 参数不合法或类型不受 Canvas 标量字段支持 |
| `INVALID_CAST_FAILURE_STRATEGY` | failureStrategy 未知 |
| `STREAM_EVENT_TIME_COLUMN_IMMUTABLE` | 流模式尝试转换事件时间字段 |

配置和平台类型参数错误由 Operator 明确返回；Cast 是否能建立计划由 Spark Analyzer 判断。预检不读取真实值，因此不能保证 `FAIL` 在运行时一定成功，也不能统计 `SET_NULL` 会产生多少 null。

## 5. 前端设计器

- Palette 增加“类型转换”，说明为“将字段显式转换为平台数据类型”。
- Inspector 拆分为 `components/processors/TypeCastProcessorInspector.tsx`。
- 顶部选择来源表和输出表；下方使用紧凑表格维护转换项。
- 每项包含字段、目标基础类型、类型参数和失败策略。
- 目标类型控件复用平台类型编辑能力：
  - STRING 按需填写 length。
  - DECIMAL 必填 precision/scale。
  - GEOMETRY 是否开放取决于 Spark Canvas 当前实际转换支持，不能只因枚举存在就展示。
- `SET_NULL` 显示“转换失败的值将变为 NULL”的明确提示。
- 流模式选择事件时间字段时即时提示禁止转换。
- 上游字段失效时保留原字段名、目标类型和策略。
- 节点摘要显示来源/输出表、转换字段数量、FAIL/SET_NULL 数量，不显示数据值。

## 6. Task Engine Operator

新增唯一 `TypeCastNodeOperator`：

- 类别为 `PROCESSOR`，支持 `BATCH`、`STREAMING`。
- 使用统一平台类型到 Spark 类型映射构造显式目标 `DataType`。
- `FAIL` 使用 ANSI cast；不得临时关闭全局 ANSI 语义。
- `SET_NULL` 使用 `try_cast` 等价表达式，并通过安全生成方式引用字段与目标类型，不能拼接用户 SQL。
- 使用一次 `select + alias` 保持字段顺序。
- Analyzer 失败返回 `SPARK_ANALYSIS_ERROR`；真实数据转换失败由 Runner 的统一异常分类器归类。
- 成功后复制输入 Map 并新增输出表。

安全摘要只记录来源表、输出表、字段名、目标平台类型和失败策略，不记录字段实际值、失败值或生成的 SQL。

## 7. 分阶段实施

### 阶段 1：协议

- 增加配置 record、失败策略枚举、节点定义和 `CanvasNodeType.TYPE_CAST`。
- 加入 Jackson/Java/TypeScript 判别联合。
- 实施时将当时 Canvas minor version 增加 1；低版本携带节点返回 `NODE_TYPE_REQUIRES_SCHEMA_VERSION`。
- 同步业务 Validator、Upgrader、Manifest 和 sealed switch。

### 阶段 2：Operator

- 完善平台类型到 Spark 类型的共享映射入口。
- 实现并注册 `TypeCastNodeOperator`。
- 更新图度数、模式能力、Compiler、批流 Runner、安全摘要和节点消息。

### 阶段 3：前端

- 更新类型、默认配置、JSON IO、Registry、Ports、Palette。
- 实现独立 Inspector 和目标平台类型编辑控件。
- 接入服务端编译输入 Schema、错误路径和节点摘要。

### 阶段 4：正式文档

- 实现后更新 `canvas-task-definition.md` 的版本、配置和失败语义。
- 同步平台类型、执行错误和日志安全说明。

## 8. 行为验收标准

- STRING、DECIMAL、日期时间和基础数值类型配置可稳定 JSON 往返。
- 同字段重复转换、无效类型参数和不存在字段返回准确错误。
- FAIL 使用 ANSI 转换语义；SET_NULL 输出字段 nullable 为 true。
- Analyzer 不支持的 Cast 在预检失败，依赖真实值的失败不被预检伪装成确定成功。
- 流式事件时间字段不能被转换。
- 未转换字段保持顺序与元数据，转换字段仍处于原位置。
- 输出 Map 保留所有输入表并新增输出表。

## 9. 不在范围内

- 自定义格式模板、时区修正、Locale 和数据库方言转换函数。
- 一项配置同时改字段名；改名使用 `RENAME`。
- 自动猜测或推荐目标类型。
- 转换失败行旁路输出、错误行数据集或质量报告。
- 平台自建完整 Cast 兼容矩阵。

## 10. 协议兼容

该节点只在真正实现时提升 minor version，不改变已有节点的类型或转换规则。旧定义升级后语义不变；正式文档和实现必须在同一改动中公布节点最低 minor version。
