# 树形码表管理

## 定位

码表用于维护规模较小、变化受控的业务枚举和分级代码，入口为“数据标准 / 码表管理”。它不是通用系统配置，也不代替数仓分层、主数据或维度表。第一版只管理元数据，不扫描物理表、不自动把查询结果中的编码替换为名称，也不保存发布历史。

## 持久化模型

`standard_dictionary` 保存全局唯一、大写化的码表编码、名称、受限的 `PlatformDataType`、启用状态、说明和从 1 开始的业务内容版本。取值类型只允许 `STRING`、`INTEGER`、`LONG`、`DECIMAL` 和 `BOOLEAN`。每次实际内容变化只递增一次版本，无变化操作不递增，不使用 JPA `@Version`。

`standard_dictionary_item` 使用 `dictionary_id`、可空 `parent_id` 和 UUID 主键表达多根、任意深度树，不冗余路径和层级。节点编码在整张码表内唯一，同级按 `sort_order、name、code` 稳定排列；移动时同时变更父节点和零基位置，并压实新旧同级顺序。服务端拒绝跨码表父节点、移动到自身或后代以及循环结构。

节点编码按码表取值类型规范化：

- `STRING` 去除首尾空白，保留大小写和前导零。
- `INTEGER`、`LONG` 校验范围并保存标准十进制文本。
- `DECIMAL` 使用精确十进制，拒绝科学计数法并移除无意义尾零。
- `BOOLEAN` 只接受 `true/false`，保存为小写。

码表、当前节点及其全部祖先同时启用时，节点才是实际可用值。停用父节点不会改写后代自身状态；所有实际启用节点都可作为值，包括非叶子节点。

## 引用和生命周期

`ds_data_model_field.standard_dictionary_id` 和 `ds_model_field_template_item.standard_dictionary_id` 都是可空 UUID 标量引用，不使用 JPA Entity 关联，也不在业务物理表中保存节点 UUID。`MANAGED`、`EXTERNAL` 与常用字段模板字段都可绑定一个码表，物理表继续保存节点编码。

新增或更换绑定要求码表启用；已经绑定的码表停用后保留引用并显示警告，可以继续保留或解除。字符串、布尔类型必须同族匹配；整数和精确小数字段只接受能够无损表达所有现有码值的数值码表，拒绝 `FLOAT/DOUBLE`。新增或修改码值时反向校验全部已绑定字段的长度、精度、范围和小数位。

码表被任一模型字段或常用字段模板字段引用后：

- 码表编码和取值类型不能修改。
- 节点编码不能修改，节点不能删除。
- 仍可新增节点、修改名称与说明、移动、启停节点和停用整张码表。

无引用码表可以在同一事务删除全部节点和定义；有子节点的单个节点不能直接删除。码表绑定只属于模型字段业务元数据，不参与 DDL、物理表指纹或物理变更计划，保存后按现有字段流程递增一次模型 Schema 版本。

## Excel

码表模板固定包含 `码表` 和 `码表项` 两张数据 Sheet。单个工作簿可包含多张码表；父节点使用同一码表内的节点编码定位，行顺序不影响解析。导入按码表编码和节点编码匹配，支持新建码表、增加节点，以及更新名称、父节点、排序、状态和说明；文件中缺失的已有节点保持不变，不执行隐式删除。

预览返回 `CREATE/UPDATE/UNCHANGED/ERROR`、规范化内容、问题、现有码表版本和状态摘要 SHA-256。提交时重新解析、校验摘要与当前版本，并在一个管理数据库事务中原子写入整个工作簿；任一问题使整批回滚。每张发生变化的已有码表只递增一次版本。导出按树的前序顺序写出父节点编码，可重新导入恢复结构。

模型元数据 Excel 当前为 V4，字段 Sheet 使用码表编码而非 UUID。导入时匹配启用码表并执行完整字段兼容性校验；V1—V3 文件没有码表列，按未绑定处理。

## 接口和权限

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/standard-dictionaries` | 使用统一 Search DSL 分页查询码表 |
| `GET` | `/api/v1/standard-dictionaries/{id}` | 查询详情、节点数、模型字段引用数和模板字段引用数 |
| `POST` | `/api/v1/standard-dictionaries` | 新建码表 |
| `POST` | `/api/v1/standard-dictionaries/{id}/actions/update` | 按内容版本修改码表 |
| `POST` | `/api/v1/standard-dictionaries/{id}/actions/enable` | 启用码表 |
| `POST` | `/api/v1/standard-dictionaries/{id}/actions/disable` | 停用码表 |
| `POST` | `/api/v1/standard-dictionaries/{id}/actions/delete` | 删除无字段引用的码表 |
| `GET` | `/api/v1/standard-dictionaries/{id}/items/tree` | 返回后端构造的递归节点树 |
| `POST` | `/api/v1/standard-dictionaries/{id}/items` | 新增根节点或子节点 |
| `POST` | `/api/v1/standard-dictionaries/{id}/items/{itemId}/actions/update` | 修改节点 |
| `POST` | `/api/v1/standard-dictionaries/{id}/items/{itemId}/actions/move` | 移动节点并压实顺序 |
| `POST` | `/api/v1/standard-dictionaries/{id}/items/{itemId}/actions/enable` | 启用节点 |
| `POST` | `/api/v1/standard-dictionaries/{id}/items/{itemId}/actions/disable` | 停用节点 |
| `POST` | `/api/v1/standard-dictionaries/{id}/items/{itemId}/actions/delete` | 删除无子节点且未受引用保护的节点 |
| `GET` | `/api/v1/standard-dictionaries/{id}/field-references` | 分页查询引用模型和字段 |
| `GET` | `/api/v1/standard-dictionaries/metadata-import-template` | 下载固定 Excel 模板 |
| `POST` | `/api/v1/standard-dictionaries/actions/query-export-metadata` | 导出选中码表 |
| `POST` | `/api/v1/standard-dictionaries/actions/query-import-preview` | 只读解析并预览 Excel |
| `POST` | `/api/v1/standard-dictionaries/actions/import-metadata` | 原子提交已预览 Excel |

查询、详情、树、模板和导出要求 `standard.dictionary.view`；所有写操作和导入要求 `standard.dictionary.manage`；字段引用明细还要求 `model.view`。模型字段保存继续使用 `model.update`。所有冲突和校验错误遵循统一 RFC 9457 Problem Details。

## 前端交互

码表列表支持编码、名称、类型和状态筛选，以及新建、导入、选中导出、启停和删除。详情默认进入“码表项”，使用后端递归 `children` 的 Ant Design 树形 Table；移动通过 TreeSelect 和同级位置完成，不启用拖拽。另有基本信息和引用字段页签。

节点因码表或祖先停用时明确显示“不可用”，已被模型字段或模板字段引用导致的编码和删除限制在操作文案中说明。码表、节点、移动和导入表单存在未保存内容时，关闭浮层、切换码表、应用内导航或浏览器离开都会提示确认。
