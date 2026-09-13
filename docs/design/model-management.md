# 模型管理第一版

## 定位与范围

模型是任务和数据服务后续可以稳定引用的结构契约。一个模型绑定一个 JDBC 数据源，并记录解析后的 Catalog、Schema、物理表名和字段定义。`MANAGED` 模型要求数据源具有“数据存储”用途；`EXTERNAL` 模型只引用已有表，可绑定任意业务用途的 JDBC 数据源。

模型管理已经连接模型所绑定的 JDBC 数据源，支持受控建表、绑定已有表、实时结构校验，以及快速预览和受控条件查询。当前 PostgreSQL、HighGo、MySQL、openGauss、人大金仓、达梦与单机 ClickHouse `MergeTree` 可生成受控建表 SQL；Oracle 和 SQL Server 仍只用于已有表，界面会明确显示“不支持由平台创建物理表”。PostgreSQL、HighGo、openGauss 与人大金仓在目标实例具备 PostGIS 兼容扩展时开放受管 Geometry。TDengine WebSocket/RESTful 只允许 `EXTERNAL` 模型绑定已有超级表，子表不属于可选资源。

物理表变更已经采用“生成计划—审阅—显式执行”的方式：PostgreSQL、HighGo、openGauss 与人大金仓共用受控原表修改和事务型影子表重建；MySQL 支持安全原表修改子集；达梦仅在运行参数满足时支持小范围原表修改；ClickHouse 只支持单条原子 `ALTER TABLE` 的安全子集。完整边界、风险和执行语义以[模型物理表演进设计](model-physical-table-evolution.md)为准。默认值/自增/索引配置、在线自由 SQL 和删除物理表仍不在当前范围内；指标管理以后作为独立业务能力通过模型和字段 UUID 引用已发布模型。

## 数据模型

表 `ds_data_model`：

| 字段 | 含义 |
| --- | --- |
| `id`、`created_at`、`updated_at` | 继承 `BaseEntity` 的 UUID 和审计时间 |
| `code` | 全局唯一且创建后不可修改的模型编码 |
| `name` | 模型显示名称 |
| `directory_id` | 可选的 `MODEL` 范围目录 UUID |
| `warehouse_layer_id` | 可选的动态数仓分层 UUID 标量引用；不建立 JPA 实体关联 |
| `storage_data_source_id` | 绑定的 JDBC 数据源 UUID；字段名为现有契约，`EXTERNAL` 模式不要求 STORAGE 用途 |
| `catalog_name`、`schema_name`、`physical_table_name` | 物理位置；Catalog 和 Schema 在选择 JDBC 数据源时按方言解析并保存快照，不由模型表单重复输入 |
| `physical_table_mode` | `MANAGED`（平台创建）或 `EXTERNAL`（绑定已有表） |
| `clickhouse_order_by_columns` | 仅 `MANAGED` 单机 ClickHouse 使用的排序键字段编码列表，固定映射到 `MergeTree ORDER BY` |
| `status` | `DRAFT`、`PUBLISHED`、`DISABLED` |
| `description` | 模型说明 |

同一 JDBC 数据源、Catalog、Schema 和物理表名组合只能被一个模型使用。删除被模型引用的数据源会返回冲突。

表 `ds_data_model_physical_statistics` 按 `model_id` 唯一保存最近一次人工采集的物理表统计快照，与模型只通过 UUID 标量关联，不使用 JPA 实体关联。快照包含行数、占用字节数、两个指标各自的 `EXACT/ESTIMATED/UNAVAILABLE` 质量、最近成功采集时间，以及最近刷新时间、状态和安全说明。刷新失败会保留上一次成功数值；物理表不存在或数据库/物理对象不支持统计时清空旧数值。修改模型物理位置、成功建表、成功执行物理结构变更和删除模型会清理快照，普通名称、目录、分层、说明或生命周期修改不会清理，也不会因为统计刷新而改变模型 `updated_at`。

模型列表按当前页模型 ID 批量读取管理库快照，详情读取当前模型快照；两者都不会因此访问外部 JDBC，第一版也不把统计值加入 Search DSL 筛选或服务端排序。人工刷新在管理库事务外通过方言读取数据库维护的快速统计，单模型查询超时为 10 秒，默认不执行 `COUNT(*)`、`ANALYZE` 或其他全表扫描。行数允许是估算值；占用空间统一表示数据、索引、分区和数据库可统计的 LOB/TOAST 等附属对象总物理占用。PostgreSQL、MySQL、ClickHouse、SQL Server、Oracle、openGauss、Kingbase 和达梦分别读取自身系统目录；TDengine 第一阶段明确不提供超级表物理统计。普通视图、权限不足、未维护数据库统计或无法确认集群总量时返回部分结果或不可获取，不伪装为精确值。

新建模型时，Catalog、Schema 统一由所选 JDBC 数据源的连接配置和数据库方言解析。修改草稿模型时，仅在切换数据源后重新解析；数据源未变化时保留原命名空间快照，避免后续修改连接配置导致已有模型静默指向其他物理表。未来如需同一连接跨多个 Schema 建模，应通过明确的高级能力扩展，而不是恢复普通表单中的自由文本输入。

表 `ds_model_warehouse_layer` 保存全局、扁平、动态定义的数仓分层，字段包括唯一编码、名称、说明、展示颜色、排序值、启用状态、可选模型编码前缀和输入分层策略。系统第一次启动该能力时一次性初始化 `ODS`、`DIM`、`DWD`、`DWS`、`ADS`；完成标记保存在不可公开查询或修改的内部系统配置中，因此用户后续删除的默认分层不会在重启后重新出现。数仓分层不是通用字典，也不按项目或目录重复维护。

常用字段模板使用 `ds_model_field_template` 和 `ds_model_field_template_item` 保存全局单字段或字段组快照。模型选用时只把兼容字段复制到当前未保存草稿，不持久化模板关系；模板后续修改、停用或删除不会传播到模型，也不触发 DDL。模板字段可以绑定码表，并参与码表安全引用保护。完整契约和冲突处理见[常用字段模板详细设计](common-field-template-management.md)。

分层的模型编码前缀必须由小写字母开头、以下划线结尾，最长 32 个字符。它只用于普通新建、JDBC 表结构创建和文件数据集 Schema 创建时生成可编辑候选，不自动修改已有模型，也不参与保存、发布或启用阻断。`ds_model_warehouse_layer_input` 使用独立 UUID 主键和 `target_layer_id`、`input_layer_id` 两个标量 UUID 保存“当前目标分层允许读取哪些输入分层”；允许自引用，不建立 JPA 多对多关联。输入策略有两种：

- `UNRESTRICTED`：不限制模型输入分层，关系列表固定为空。
- `ALLOW_LIST`：只规划允许输入的分层；空列表明确表示不允许任何模型分层输入。

允许输入分层当前仅是建模规划元数据，不读取任务定义、不分析 SQL，也不参与任务保存、发布或运行校验。新增关系只能指向启用分层；已经配置的停用分层可以保留或移除。被模型引用的分层不能删除，被其他分层作为允许输入引用的分层同样不能删除；删除分层时会清理以它自身为目标的输出关系。

第二个内部一次性初始化标记只为仍然存在且尚未配置规则的默认分层补充推荐值，不覆盖用户配置，也不重新创建已删除分层：ODS 使用 `ods_` 且允许列表为空；DIM 使用 `dim_` 并允许 ODS、DIM；DWD 使用 `dwd_` 并允许 ODS、DIM、DWD；DWS 使用 `dws_` 并允许 DWD、DIM、DWS；ADS 使用 `ads_` 并允许 DWD、DWS、DIM、ADS。自定义分层默认无前缀且输入不受限制。

模型可以不分层，已有模型升级后保持“未分层”。停用分层不会清除已有模型引用，详情和列表继续显示并标注“已停用”；新建、导入或把其他模型改到该分层会被拒绝。已有模型可以保留当前停用分层、清空或改选启用分层。被任一模型引用的分层不能删除，也不能修改编码；名称、说明、颜色、排序和启停状态仍可维护。分层元数据变化不增加模型 Schema 版本，不生成物理变更计划，也不影响建表 DDL。

表 `ds_data_model_field`：

| 字段 | 含义 |
| --- | --- |
| `id`、`created_at`、`updated_at` | UUID 和审计时间 |
| `model_id` | 模型 UUID 标量引用 |
| `code`、`name` | 字段编码和显示名称 |
| `field_type` | 数据库无关的字段类型 |
| `field_length` | 字符串长度 |
| `numeric_precision`、`numeric_scale` | 小数精度与小数位 |
| `geometry_kind`、`crs_authority`、`crs_code`、`coordinate_dimension` | Geometry 子类型、稳定 CRS 与坐标维度；标量字段为空 |
| `nullable`、`primary_key` | 是否允许为空、是否为主键字段 |
| `sort_order` | 字段展示和未来建表顺序 |
| `description` | 字段说明 |
| `standard_dictionary_id` | 可选的树形码表 UUID 标量引用；只属于业务元数据，不参与物理表结构 |
| `physical_column_role` | 外部 TDengine 超级表字段的 `TIME_KEY/TAG/REGULAR` 物理语义；其他数据库和历史空值按 `REGULAR` 处理 |

字段使用与 Spark SQL 语义对齐的平台标量类型。完整类型、参数和数据库双向映射规则见[模型平台数据类型设计](model-data-type-system.md)。模型字段使用独立 UUID；整体保存字段时会保留仍存在字段的 UUID，为以后任务、服务和指标引用提供稳定标识。

## 生命周期

- 新建模型默认为 `DRAFT`，默认使用 `MANAGED` 模式，可以修改物理位置、物理表来源和字段定义。
- `MANAGED` 模式下，用户可以先查看系统生成的 DDL 并显式执行“创建物理表”；如果发布时物理表仍不存在，发布动作会使用相同受控 DDL 自动创建。支持范围由具体方言能力决定。单机 ClickHouse 固定使用 `MergeTree()`，排序键仅允许引用非 Geometry 的简单字段编码；空排序键显式使用 `tuple()`。
- Geometry 受管建表支持已安装 PostGIS 的 PostgreSQL、MySQL 8.x InnoDB 和单机 ClickHouse，必须使用 EPSG + XY，且不创建空间索引。PostGIS/MySQL 在查看 DDL、建表和导入预览时通过目标连接校验运行能力以及 EPSG 到本地 SRID/SRS ID 的唯一映射；ClickHouse 使用 `String` / `Nullable(String)` 保存原始 WKB，并在列 comment 中写入固定 marker。
- `EXTERNAL` 模式下，用户可从任意已启用 JDBC 数据源的默认命名空间选择一张普通表，不限制 SOURCE、STORAGE 或 DISTRIBUTION 用途；TDengine 例外地只允许选择超级表，子表会在服务端再次校验并拒绝。创建或更换绑定表时，服务端在管理数据库事务外读取并映射元数据，再使用短事务保存模型和导入字段；系统绝不修改该表。
- `EXTERNAL` 模式的物理字段以数据库为准：字段编码、类型、长度/精度、是否可空和主键不可在平台修改，只允许调整字段名称、说明、展示排序和关联码表。TDengine 还会保留时间列、指标列和 TAG 的物理角色；数据库为纳秒精度或字段类型无法无损映射到当前模型契约时，创建会被拒绝并指出字段。
- `MANAGED` 和 `EXTERNAL` 字段都可以绑定一个兼容码表。绑定只增加一次模型 Schema 版本，不进入 DDL、结构指纹或物理变更计划；码表停用后保留已有绑定并允许解除。完整规则见[树形码表管理](standard-dictionary-management.md)。
- 当前模型契约的物理标识符统一采用小写字母、数字和下划线；外部表或字段名称不符合该规则时会明确拒绝导入，避免后续任务或服务引用错误列名。
- 草稿和已停用模型统一通过“发布”进入 `PUBLISHED`。发布要求至少存在一个字段、关联 JDBC 数据源存在且已启用；受管物理表不存在时自动创建，已存在时必须实时检查为 `MATCHED`。外部表只检查，不执行 DDL。
- 已发布模型的物理位置和字段定义锁定，名称、目录和说明仍可修改。
- 数仓分层及其前缀、允许输入规则属于业务组织和规划元数据，草稿、已发布和已停用模型都可调整；它不改变模型 Schema 版本或物理表定义。
- 已创建且包含 Geometry 的匹配受管表只开放约束级结构修改：可以调整 nullable 和非 Geometry 字段的主键，通过统一物理变更计划审阅和执行；字段编码、类型、Geometry kind/CRS/dimension、新增和删除字段继续锁定。PostgreSQL/PostGIS 可生成原表约束 DDL；ClickHouse nullable 当前返回不可执行的 `UNSUPPORTED` 计划，其字段主键仅作为平台元数据直接保存，不生成 ClickHouse 约束或唯一性保证；达梦暂不支持受管 Geometry。
- 已发布模型可以停用；已停用模型再次发布时重新执行完整物理表就绪检查。
- 已发布模型不能直接删除，需先停用。删除只删除模型和字段元数据，不删除物理表。

发布采用“短事务读取模型、字段和数据源快照 → 管理库事务外检查物理表并按需执行受管建表 → 短事务复核来源状态和模型更新时间后提交 `PUBLISHED`”的边界。外部 JDBC 检查和 DDL 不进入管理数据库事务；如果物理表创建成功后模型被并发修改或最终状态提交失败，系统不会删除已经创建的表，模型保持原状态，用户重试发布时按已有匹配表继续。

`ModelPhysicalTablePort` 是模型编排与 JDBC 实现的显式边界。方言模块接收数据库无关的建表定义，负责标识符引用、字段类型映射、结构比较和受控 SQL 渲染；业务模块不拼接 SQL。

结构检查默认采用严格匹配：字段名、类型、字符串长度、小数精度/小数位、是否可空、主键字段及顺序必须一致；字段顺序和普通注释不参与比较。ClickHouse 额外校验 `MergeTree` 引擎和排序键，但其物理 `String` 不保存模型字符串长度，也没有关系型唯一主键约束；这些差异由 ClickHouse 方言明确归一。ClickHouse Geometry 的保留 marker 属于结构声明，encoding、kind、CRS、dimension 或 nullable 任一不一致都会导致结构不匹配。差异会按缺少字段、多余字段、类型、长度、精度、空值约束、主键和存储配置返回。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/models` | 使用统一 Search DSL 分页查询模型 |
| `GET` | `/api/v1/models/{id}` | 查询模型及字段详情 |
| `GET` | `/api/v1/models/{id}/related-tasks` | 使用统一 Search DSL 分页查询最后保存任务定义中引用当前模型的任务；可按 `INPUT/OUTPUT` 角色过滤 |
| `GET` | `/api/v1/models/{id}/lineage/table` | 按上游、下游或双向查询当前表级血缘，并在下游展示标准数据服务，支持 1～2 次任务转换 |
| `GET` | `/api/v1/models/{id}/lineage/fields/{fieldId}` | 查询属于当前模型的指定字段血缘及标准服务暴露关系，支持 1～2 次任务转换 |
| `POST` | `/api/v1/models/{id}/lineage/actions/query-fields` | 批量查询字段血缘；默认前 20 个、最多 50 个，空数组表示不展示字段 |
| `GET` | `/api/v1/models/platform-types` | 查询指定数据存储支持的平台字段类型 |
| `GET` | `/api/v1/models/external-table-import-preview` | 预览已有表导入后的平台字段和映射质量 |
| `POST` | `/api/v1/models/managed-import-preview` | 将任意已启用 JDBC 源表映射为目标数据存储上的受管模型字段候选；不保存模型、不执行 DDL |
| `POST` | `/api/v1/models/file-dataset-import-preview` | 将已就绪文件数据集逻辑表的 Schema 映射为目标数据存储上的受管模型字段候选；只读管理库元数据，不读取文件、不执行 DDL |
| `POST` | `/api/v1/models/managed-drafts` | 原子保存 `MANAGED + DRAFT` 模型及已校对字段；要求目标物理表尚不存在 |
| `GET` | `/api/v1/models/metadata-import-template` | 下载固定版本的空白 `.xlsx` 模型元数据模板 |
| `POST` | `/api/v1/models/actions/preview-metadata-import` | 解析并校验 `.xlsx` 模型元数据；只读预览，不保存模型、不执行 DDL |
| `POST` | `/api/v1/models/actions/import-metadata` | 将整份已校对的 Excel 模型元数据原子创建为 `MANAGED + DRAFT`；任意失败整批回滚 |
| `POST` | `/api/v1/models/actions/export-metadata` | 将选中的 `MANAGED` 模型及字段元数据导出为 `.xlsx`；`EXTERNAL` 暂不支持 |
| `GET` | `/api/v1/models/{id}/physical-table` | 实时检查目标物理表和结构差异 |
| `POST` | `/api/v1/models/{id}/actions/refresh-physical-statistics` | 在事务外读取数据库系统统计并保存物理表统计快照；外部连接类失败以刷新状态返回 |
| `GET` | `/api/v1/models/{id}/physical-table/ddl` | 预览平台生成的建表 SQL |
| `GET` | `/api/v1/models/{id}/data-preview` | 快速预览最多 50 行数据；不接收筛选、排序、分页参数，也不执行总数统计 |
| `GET` | `/api/v1/models/{id}/spatial-preview` | 检查 PostgreSQL/PostGIS 动态空间预览能力、Geometry 字段、索引和已保存的模型物理统计行数 |
| `GET` | `/api/v1/models/{id}/spatial-preview/map` | 按 EPSG:3857 bbox 和图片尺寸返回透明 PNG；不返回 Geometry 或属性 |
| `POST` | `/api/v1/models/{id}/actions/query-data` | 只读条件查询；请求体支持字段选择、AND/OR 顶层条件、筛选、最多 3 个排序、分页和可选总数 |
| `POST` | `/api/v1/models` | 新建草稿模型 |
| `POST` | `/api/v1/models/{id}/actions/update` | 修改模型元数据 |
| `POST` | `/api/v1/models/{id}/actions/update-fields` | 整体保存字段定义 |
| `POST` | `/api/v1/models/{id}/actions/create-physical-table` | 创建 `MANAGED` 物理表并再次校验 |
| `POST` | `/api/v1/models/{id}/actions/publish` | 发布草稿或已停用模型；缺失的受管物理表自动创建 |
| `POST` | `/api/v1/models/{id}/actions/disable` | 停用模型 |
| `POST` | `/api/v1/models/{id}/actions/delete` | 删除模型元数据 |
| `GET` | `/api/v1/model-warehouse-layers` | 使用统一 Search DSL 分页查询动态数仓分层、编码前缀、允许输入分层、模型引用数和分层规范反向引用数 |
| `POST` | `/api/v1/model-warehouse-layers` | 新建数仓分层及建模规范；旧请求缺省规则时按无前缀、`UNRESTRICTED` 处理 |
| `POST` | `/api/v1/model-warehouse-layers/{id}/actions/update` | 原子修改数仓分层及允许输入关系；被模型引用时分层编码不可修改 |
| `POST` | `/api/v1/model-warehouse-layers/{id}/actions/enable` | 启用数仓分层 |
| `POST` | `/api/v1/model-warehouse-layers/{id}/actions/disable` | 停用数仓分层 |
| `POST` | `/api/v1/model-warehouse-layers/{id}/actions/delete` | 删除未被模型或其他分层规范引用的数仓分层 |
| `GET` | `/api/v1/model-field-templates` | 使用统一 Search DSL 分页查询常用字段模板及字段快照 |
| `GET` | `/api/v1/model-field-templates/{id}` | 查询常用字段模板详情 |
| `POST` | `/api/v1/model-field-templates` | 原子新建模板及字段 |
| `POST` | `/api/v1/model-field-templates/{id}/actions/update` | 原子修改模板及字段 |
| `POST` | `/api/v1/model-field-templates/{id}/actions/enable` | 启用模板，允许新选用 |
| `POST` | `/api/v1/model-field-templates/{id}/actions/disable` | 停用模板，不影响已复制字段 |
| `POST` | `/api/v1/model-field-templates/{id}/actions/delete` | 删除模板，不影响已复制字段 |

分层查询可由 `system.configuration.view` 或模型查看、创建、更新权限调用，以支持系统管理页和模型表单；分层写操作统一要求 `system.configuration.update`。

物理统计刷新要求 `model.view`。模型不存在仍返回标准 `404 ProblemDetail`；外部连接、权限和查询超时等采集失败保存为 `FAILED` 快照并返回 200，使单项失败不会中断前端批量刷新。

## 前端交互

模型页复用通用 `MODEL` 目录树，采用左树右表的紧凑布局。列表支持名称/编码、状态、数仓分层、JDBC 数据源和目录筛选，表格占满剩余高度并在表体内部滚动。数仓分层在“系统管理 / 数仓分层”中动态维护，不与模型目录层级相互替代：目录表达资产归属和导航，数仓分层表达数据加工阶段。

列表“数据统计”列展示最近快照：估算行数使用 `≈`，占用空间按 1024 进制显示，Tooltip 保留完整数值、质量和采集时间。单行可以刷新；勾选后最多一次刷新 20 个模型，前端最多三个并发且单项失败不终止其他项，全部结束后只重新读取一次模型列表。`MANAGED` 和 `EXTERNAL` 都可选择和刷新；选择中包含 `EXTERNAL` 时仅禁用 Excel 结构导出。工具栏原有“刷新列表”只重新读取管理库快照，不触发外部数据库统计。

模型列表支持一次选择最多 50 个模型批量发布，前端最多三个并发复用单模型发布接口，不新增整批事务。草稿和已停用模型参与发布，已发布模型跳过；各模型独立检查并按需创建受管物理表，允许部分成功。结束后统一刷新一次查询，失败项保留选中并展示模型名称和服务端原因。

模型列表使用一个“新建模型”下拉入口，依次提供“手动创建、从数据源表创建、从文件数据集创建”，分隔线后提供“从 Excel 模板导入”。创建方式使用“从……创建”描述一次性复制结构，避免被理解为导入业务数据。新建和修改使用紧凑抽屉，可选一个启用的数仓分层，并只选择 JDBC 数据源和物理表名，不重复填写 Catalog 或 Schema。普通新建时，如果模型编码仍为空，选择分层会填入其前缀；切换分层只会替换仍等于旧自动前缀的编码，人工输入永不覆盖。模型基本信息展示当前分层的编码规范，但不判断现有编码是否合规。编辑引用停用分层的模型时允许原值保留或清空，但不能把其他模型新分配到停用分层。`MANAGED` 模式只列出具有数据存储用途的数据源；选择“绑定已有表”后，列表扩展为全部已启用的 JDBC 数据源，并从默认命名空间加载普通表、展示字段预览和导入类型；存在不支持字段时不能保存。新建完成后直接进入独立模型详情页的字段工作区。

模型创建菜单中的“从数据源表创建”用于把多张 JDBC 普通表的一次性结构快照复制为多个 `MANAGED + DRAFT` 模型。来源可以是任意已启用 JDBC 数据源且不限制业务用途；一个批次统一选择具有 `STORAGE` 用途的目标 JDBC 数据源、可选目录和可选数仓分层，批量分层用于初始化草稿，每个模型仍可覆盖或清空，且不会从来源表推断分层。草稿模型编码候选为“最终所选分层前缀 + 小写源表名”，源表名已有相同前缀时不重复添加；非法、超长或批次内重复候选仍留空标红。批量或单模型切换分层只更新未被人工修改的编码。每个模型可以单独修改目标物理表名。预览会依次执行源物理类型到平台类型、平台类型到目标物理类型的安全映射，并带入字段编码、名称、长度/精度、可空、主键、顺序和注释；不合法标识符或不安全类型保持未解决状态，由用户补齐后再提交。默认值、自增、生成列和索引等当前模型无法表达的信息只展示警告。每个模型及其字段原子保存，批量请求最多三个并发且允许部分成功；整个导入过程不绑定源表、不导入数据，也不执行建表 DDL。用户调整字段后可以在详情页显式创建物理表，也可以在发布时由系统自动创建缺失的受管表。普通新建中的 `EXTERNAL` 模式继续负责绑定已有表，两种语义互不替代。Excel 导入始终保留文件中的模型编码，不按分层前缀自动改写。

“从文件数据集创建”选择一个文件数据集中的多张 `READY/SCHEMA_READY` 逻辑表，只读取管理数据库中已解析的 `PlatformTypeDefinition`，不会读取文件内容或重新触发解析。`QUEUED/PARSING` 和空 Schema 表保留展示但禁止选择。一个批次统一选择目标 `STORAGE` JDBC、目录和分层，每张表可以独立修改模型编码、名称、分层和目标物理表名；字段名称同时作为字段编码候选，编码只小写化，非法或重复时留空。主键和字段说明因文件 Schema 没有对应信息分别使用 `false` 和空值。CSV、TSV、TXT、JSON、JSONL、Excel 标记为样本推断，Parquet、Avro、GDB、SHP 标记为声明 Schema；目标方言的 `LOSSY/UNSUPPORTED` 映射保持未解决。创建继续逐项调用 `/managed-drafts`，最多三个并发并允许部分成功和只重试失败项。来源数据集和逻辑表 ID 不进入模型保存请求，因此创建后完全解耦；后续文件数据写入必须通过任务完成，并由任务定义与运行血缘表达真实关系。校对内容修改后关闭抽屉、返回来源步骤或切换结构上下文时必须确认放弃。

模型详情的“关联任务”页签查询任务最后一次成功保存的定义。Local SQL 输入来自显式输入模型表，输出来自定义中的输出模型；Canvas 只读取 `MODEL_INPUT/MODEL_OUTPUT` 引用索引，JDBC、Kafka、文件和 HTTP 节点不产生模型关系。同一任务多次引用当前模型时按任务聚合，保留全部节点位置和 `INPUT/OUTPUT` 角色。该能力不保存新的关系实体、不分析 SQL 文本，也不表示历史运行血缘。接口同时要求 `model.view` 和 `task.view`，前端在缺少任务查看权限时不显示该页签。

### Excel 模型元数据交换

Excel 能力只交换平台已经持久化的表结构元数据，不是环境备份，也不读取或写入物理表数据。第一版只接受系统模板生成的 `.xlsx` 文件；导入结果固定为 `MANAGED + DRAFT`，导出只允许 `MANAGED` 模型。物理行数、占用空间及刷新状态属于环境快照，不进入 Excel 导入导出。文件不包含数据源连接、数据源或目录 UUID、Catalog、Schema、模型或字段 UUID、生命周期状态、来源绑定和审计时间。导入界面只统一选择一个已启用且具有 `STORAGE` 用途的目标 JDBC 数据源；模型目录由 V5 文件的每个模型行独立定义，因此同一文件可以包含多个目录中的模型，并继续支持跨环境、跨受支持数据库方言交换。

当前写出格式为 V5，继续兼容导入 V1、V2、V3 和 V4。五个版本均固定包含三个工作表：

| 工作表 | 固定内容 |
| --- | --- |
| `说明` | 文件标识 `DATASCALPEL_MODEL_METADATA`、格式版本、填写规则和支持的平台字段类型 |
| `模型` | V1/V2 包含模型编码、模型名称、目标物理表名、模型说明、可选 ClickHouse 排序键；V3/V4 增加数仓分层编码和展示名称；V5 再增加模型目录路径 |
| `字段` | V1 包含所属模型编码、字段编码、字段名称、平台字段类型、长度、精度、小数位、可空、主键、排序值和字段说明；V2/V3 追加几何类型、CRS Authority、CRS Code、坐标维度；V4/V5 再追加码表编码 |

字段类型使用 `PlatformDataType`，不在文件中保存 MySQL、PostgreSQL 等数据库原生类型。V2—V5 Geometry 保存平台 kind、EPSG code 和 XY，不保存本地 SRID/SRS ID 或 ClickHouse WKB marker。V3—V5 导入只按大写化后的分层编码匹配当前启用定义，分层名称仅用于导出展示；空编码表示未分层，未知、非法或停用编码会在校对页标红，用户可以改选启用分层或清空。V1/V2 一律按未分层导入。V4/V5 的码表编码同样大写化后匹配当前启用定义，并验证字段类型、长度、精度、范围和小数位能否安全表达全部码表值；未知、停用或不兼容码表保持未解决，用户可改选兼容码表或清空。V1—V3 缺少该列时按未绑定处理。预览会使用目标方言验证平台类型能否安全映射；`LOSSY` 或 `UNSUPPORTED` 映射保持未解决并阻止整份文件创建。ClickHouse 排序键以逗号分隔的字段编码保存，Geometry 字段不能作为排序键；导入到非 ClickHouse 目标时忽略并给出警告。默认值、自增、生成列和索引因当前模型契约无法表达，不进入文件，也不会伪造成其他配置。

V5 模型目录路径使用 `/` 分隔，例如 `DW/水库工程`，逐级忽略大小写匹配已有的 `MODEL` 目录；空值表示未分类。导入不会创建、修改或猜测目录，也不要求 `directory.manage`。路径不存在、包含空层级、无法唯一匹配或目录树自身异常时，校对页在对应模型行展示问题并阻止整份文件提交。V1—V4 没有目录列，继续读取为未分类并显示明确警告。导出会写回模型当前完整目录路径，因而 V5 文件重新导入时能够保留目录归属。

解析阶段不保存任何实体。文件级结构问题（模板标识、版本、工作表或表头错误）直接拒绝；模型和字段内容问题在校对页标红，并尽量一次展示整份文件中的目录、分层、码表、字段、类型映射、排序键、模型编码、物理位置和目标表占用问题。用户可以修改可编辑模型信息并通过字段弹窗补齐空值；目录错误必须修改 Excel 或先维护目录后重新解析。主键字段不得可空，字段编码在模型内必须唯一，排序值必须是大于等于零的整数，目标模型编码和物理位置不得冲突，目标物理表必须能够实时确认不存在。任一文件级、模型级或字段级问题都会阻止整份文件提交。单文件上限为 10 MB、200 个模型、每模型 500 个字段和总计 20,000 个字段，并拒绝模板数据单元格中的公式。

校对完成后只调用一次 `/api/v1/models/actions/import-metadata`。服务端先在管理事务外重新解析目录并逐模型检查目标 JDBC 物理表，所有外部检查通过后再在一个短事务中保存整批模型和字段；正式保存时再次校验目录、分层、数据源、模型编码和物理位置。任一模型保存失败都会回滚整批，数据库中不留下部分模型，也不提供“重试失败项”。预览、Excel 导入和导出均不调用物理表创建端口；用户随后可以在模型详情页查看 DDL、二次确认并调用 `/actions/create-physical-table`，也可以在发布时自动创建缺失的受管物理表。原有单模型 `/managed-drafts` 以及“从数据源表创建”的逐模型行为保持不变。

导出入口包括列表选中项、列表单模型操作和模型详情页。列表中的 `EXTERNAL` 模型不可选择，服务端也会拒绝任何包含 `EXTERNAL` 的导出请求。导出的文件与空白模板同构，可以编辑后重新导入；重新导入始终执行“创建新草稿”，不会通过编码更新或覆盖已有模型。

详情页采用 Tab 结构，第一版包含基础信息、字段信息、物理变更、数据查询、关联任务和血缘分析六个工作区。受管模型的字段工作区支持新增、从启用的常用字段模板复制、修改、删除和整体保存；复制时会跳过同编码字段及目标存储不支持的类型，停用码表绑定留空，所有降级均明确提示。外部表模型只开放字段名称、说明、排序和关联码表；模型发布后全部转为只读。

基础信息、字段信息、数据预览、关联任务和血缘分析均使用真实接口。基础信息展示物理表来源、实时状态、建表 SQL 和结构差异；仅受管表显示创建物理表操作。数据预览只在物理表结构匹配时读取真实数据：快速预览固定最多读取 50 行；条件查询可选择返回字段、维护多个筛选和最多 3 个排序，并按 20、50、100 条分页；包含 Geometry 时增加 PostgreSQL/PostGIS 空间预览。空间预览使用无底图 MapLibre，固定显示 CRS 为 EPSG:3857，PostGIS 按当前视口过滤、转换、裁剪和简化，服务端返回透明 PNG，不向浏览器返回 Geometry。初始范围固定为江西，不扫描全表 extent；平台只识别既有 GiST/SP-GiST 索引而不创建索引，无索引时复用管理库中已保存的模型物理统计，仅允许行数不超过 50,000 的小表受限预览。统计行数为空时页面提供“刷新物理统计”，刷新后自动重新检查；仍不可用时再提示执行 `ANALYZE` 或创建空间索引。条件查询默认不执行 `count(*)`，而是多取一行判断下一页；单条查询超时 15 秒、最多 20 个筛选条件、最大分页偏移 10,000。所有字段名都由模型字段白名单解析为受控物理列，二进制和 Geometry 字段默认不返回，也不能显式返回、筛选、排序、分组或聚合，绝不接收任意 SQL。未定义排序且无主键/ClickHouse 排序键时，页面会提示翻页顺序可能不稳定。

关联任务读取任务最后一次成功保存的定义。血缘分析读取任务当前生效的不可变血缘快照，并在查询期组合标准数据服务终端节点，支持表级/字段级、上下游方向和一至两次任务转换；同一模型关联的多个服务全部保留。表级图按有向数据流拓扑自动分层，依次排列输入资产、加工任务、输出模型和数据服务，同层多个资产纵向排列，不再把任务与其输入资产挤在同一列。字段级按照所属模型、JDBC 表或外部资源组织为表卡片，表头展示资产信息，字段按顺序显示为卡片内的独立连接行，任务节点位于输入表与输出表之间。默认展示前 20 个字段，可搜索多选并最多展示 50 个；悬停字段临时高亮关联路径，点击锁定，点击空白或按 Esc 清除。覆盖、陈旧和截断警告通过覆盖标签右侧的紧凑提示按需查看，不占用独立提示行。字段用途与值来源分别展示，模型 Schema 版本变化后的旧关系标记为陈旧，图超过 200 个节点或 600 条边时明确标记字段级和总体截断。没有血缘关系时显示正式空状态，不生成 Mock 数据。血缘页签及接口同时要求 `model.view`、`task.view` 和 `service.view`；详细规则见[血缘接入契约 V3](lineage-integration-contract.md)。

列表的筛选、目录、分页和每页数量保存在 URL 查询参数中。从模型名称或字段入口进入详情后，返回列表会恢复之前的查询上下文。
