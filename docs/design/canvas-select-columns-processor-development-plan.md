# Canvas `SELECT_COLUMNS` Processor 开发计划

## 1. 目标与范围

新增 `SELECT_COLUMNS` Processor，从一张上游逻辑表中按用户指定顺序选择字段，并以新的逻辑表名输出。

首期只负责字段裁剪和排序：

- 不承担字段改名；字段改名继续使用 `RENAME`。
- 不包含表达式或类型转换。
- 同一节点同时支持 `BATCH`、`STREAMING`。
- 预检与 Runner 使用唯一无状态 `SelectColumnsNodeOperator`。

节点类别为 `PROCESSOR`。图规则为恰好一条入边、至少一条出边。

## 2. 稳定配置协议

```ts
interface SelectColumnsConfiguration {
  sourceTableName: string;
  outputTableName: string;
  columns: string[];
}
```

示例：

```json
{
  "sourceTableName": "orders",
  "outputTableName": "order_summary",
  "columns": [
    "order_id",
    "customer_id",
    "amount",
    "created_at"
  ]
}
```

协议规则：

- `columns` 至少包含一个字段。
- 字段名按元数据原始值精确匹配。
- 数组顺序就是输出 Schema 顺序。
- 同一字段不得重复出现。
- 不保存字段 Schema、索引号、X6 状态或物理数据库类型。

## 3. 输入、输出与 Schema 传播

Operator 从合并后的上游 Map 中精确查找 `sourceTableName`，通过单次 Spark `select` 按配置顺序投影字段。

输出 Map 规则：

- 保留输入 Map 中所有现有表。
- 以 `outputTableName` 新增投影结果。
- 输出名与任一现有 Key 冲突时返回 `DUPLICATE_TABLE_NAME`，包括与来源表同名。

输出 Schema：

- 只包含 `columns` 中的字段，顺序与配置完全一致。
- 被选择字段的 `PlatformTypeDefinition`、nullable、长度、精度、注释等完整平台元数据从来源字段继承。
- `origin` 和 `datasetKind` 继承来源表。
- 如果来源 `eventTimeColumn` 仍在输出字段中，则同时继承 `eventTimeColumn` 和 `watermarkDelay`。
- 如果事件时间字段被删除，则输出同时清空 `eventTimeColumn` 和 `watermarkDelay`，不得保留指向不存在字段的流式属性。

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
| `EMPTY_COLUMN_SELECTION` | `columns` 为空 |
| `DUPLICATE_SELECTED_COLUMN` | 同一字段被选择多次 |

校验必须返回数组项的准确路径，例如 `configuration.columns[2]`。上游表无效但已有安全输入上下文时，Inspector 仍应获得 `inputTables`。

## 5. 前端设计器

- Palette 增加“选择字段”，说明为“裁剪字段并定义输出字段顺序”。
- Inspector 独立为 `components/processors/SelectColumnsProcessorInspector.tsx`。
- 来源表来自编译结果的 `inputTables`；选择后展示可排序字段列表。
- 推荐采用双区或勾选列表交互：
  - 左侧/上方为可用字段搜索。
  - 右侧/下方为已选字段，可拖动排序。
  - 提供“全选”“清空”，但清空状态应立即显示必填错误。
- 字段行显示名称、平台类型、nullable，并对已失效字段保留原位置且标红。
- 切换来源表时不自动重置旧选择；通过错误状态提示用户显式处理。
- 节点摘要显示来源表、输出表和已选字段数量，不列出全部字段。

## 6. Task Engine Operator

新增唯一 `SelectColumnsNodeOperator`：

- `category()` 为 `PROCESSOR`。
- `supportedModes()` 为 `BATCH`、`STREAMING`。
- 使用来源 Dataset 的安全列引用构造单次 `select`。
- 以 Analyzer 验证投影计划，但字段存在性和重复字段先使用稳定业务错误表达。
- 成功后复制输入 Map 并新增输出表，不改变来源 Map。

节点阶段为 `PROCESS`。安全摘要只记录来源表、输出表、字段数量和字段名，不记录数据值。失败走统一错误分类和节点生命周期包装。

## 7. 分阶段实施

### 阶段 1：稳定契约

- 增加 Java 配置 record、节点定义和 `CanvasNodeType.SELECT_COLUMNS`。
- 接入 `CanvasNodeDefinition` sealed 联合、Jackson 判别类型和业务层 Canvas 定义。
- 实施时把当时 minor version 增加 1；Upgrader 拒绝更低版本携带该节点。
- 同步 Validator、Manifest 转换、安全消息及所有 sealed switch。

### 阶段 2：统一 Operator

- 实现并注册 `SelectColumnsNodeOperator`。
- 在 `CanvasGraphPlan` 中声明单入、多出规则和批流能力。
- 接入 Compiler、批 Runner 和流 Runner 的共同执行路径。

### 阶段 3：前端

- 更新 `canvasTypes`、默认配置、JSON IO、序列化、Registry、Ports 和 Palette。
- 实现独立 Inspector、搜索与选择排序交互。
- 接入编译输入 Schema、错误定位和节点摘要。

### 阶段 4：正式文档

- 实现完成后更新 `canvas-task-definition.md` 的节点清单、版本历史和完整示例。
- 同步执行和安全日志说明。

## 8. 行为验收标准

- 字段可按任意配置顺序输出，JSON 导入导出后顺序不变。
- 空选择、重复选择和不存在字段返回稳定错误及准确路径。
- 输出表新增而来源表仍在 Map 中。
- 字段平台元数据完整保留。
- 删除事件时间字段时，事件时间和 Watermark 同时清空。
- 批流两种模式使用同一配置和 Operator。
- 上游字段变化后旧选择不被静默删除。

## 9. 不在范围内

- 字段重命名、表达式、常量列和类型转换。
- 通配符持久化；“全选”必须展开为当时明确的字段名数组。
- 嵌套字段路径、Struct/Array/Map 投影。
- 根据目标 Output 自动裁剪字段。

## 10. 协议兼容

新增节点只提升 Canvas minor version，不改变现有节点语义。旧定义升级后保持原节点、连线和布局不变。只有全链路实现时才把 `SELECT_COLUMNS` 加入正式协议；本计划本身不预占版本号。
