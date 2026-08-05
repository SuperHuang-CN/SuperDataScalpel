# 模型管理第一版

## 定位与范围

模型是任务和数据服务后续可以稳定引用的结构契约。一个模型绑定一个 JDBC 数据源，并记录解析后的 Catalog、Schema、物理表名和字段定义。`MANAGED` 模型要求数据源具有“数据存储”用途；`EXTERNAL` 模型只引用已有表，可绑定任意业务用途的 JDBC 数据源。

模型管理已经连接模型所绑定的 JDBC 数据源，支持受控建表、绑定已有表、实时结构校验，以及快速预览和受控条件查询。当前 PostgreSQL、MySQL 与单机 ClickHouse `MergeTree` 可生成受控建表 SQL；其他已登记数据库仍可用于元数据读取，但界面会明确显示“不支持由平台创建物理表”。

物理表变更已经采用“生成计划—审阅—显式执行”的方式：PostgreSQL 支持受控原表修改与事务型重建，达梦仅在运行参数满足时支持小范围原表修改，ClickHouse 只支持单条原子 `ALTER TABLE` 的安全子集。完整边界、风险和执行语义以[模型物理表演进设计](model-physical-table-evolution.md)为准。默认值/自增/索引配置、在线自由 SQL 和删除物理表仍不在当前范围内；指标管理以后作为独立业务能力通过模型和字段 UUID 引用已发布模型。

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

新建模型时，Catalog、Schema 统一由所选 JDBC 数据源的连接配置和数据库方言解析。修改草稿模型时，仅在切换数据源后重新解析；数据源未变化时保留原命名空间快照，避免后续修改连接配置导致已有模型静默指向其他物理表。未来如需同一连接跨多个 Schema 建模，应通过明确的高级能力扩展，而不是恢复普通表单中的自由文本输入。

表 `ds_model_warehouse_layer` 保存全局、扁平、动态定义的数仓分层，字段包括唯一编码、名称、说明、展示颜色、排序值、启用状态、可选模型编码前缀和输入分层策略。系统第一次启动该能力时一次性初始化 `ODS`、`DIM`、`DWD`、`DWS`、`ADS`；完成标记保存在不可公开查询或修改的内部系统配置中，因此用户后续删除的默认分层不会在重启后重新出现。数仓分层不是通用字典，也不按项目或目录重复维护。

常用字段模板使用 `ds_model_field_template` 和 `ds_model_field_template_item` 保存全局单字段或字段组快照。模型选用时只把兼容字段复制到当前未保存草稿，不持久化模板关系；模板后续修改、停用或删除不会传播到模型，也不触发 DDL。模板字段可以绑定码表，并参与码表安全引用保护。完整契约和冲突处理见[常用字段模板详细设计](common-field-template-management.md)。

分层的模型编码前缀必须由小写字母开头、以下划线结尾，最长 32 个字符。它只用于普通新建和 JDBC 表结构导入时生成可编辑候选，不自动修改已有模型，也不参与保存、发布或启用阻断。`ds_model_warehouse_layer_input` 使用独立 UUID 主键和 `target_layer_id`、`input_layer_id` 两个标量 UUID 保存“当前目标分层允许读取哪些输入分层”；允许自引用，不建立 JPA 多对多关联。输入策略有两种：

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

字段使用与 Spark SQL 语义对齐的平台标量类型。完整类型、参数和数据库双向映射规则见[模型平台数据类型设计](model-data-type-system.md)。模型字段使用独立 UUID；整体保存字段时会保留仍存在字段的 UUID，为以后任务、服务和指标引用提供稳定标识。

## 生命周期

- 新建模型默认为 `DRAFT`，默认使用 `MANAGED` 模式，可以修改物理位置、物理表来源和字段定义。
- `MANAGED` 模式下，用户先查看系统根据模型生成的 DDL，再明确执行“创建物理表”；支持范围由具体方言能力决定。单机 ClickHouse 固定使用 `MergeTree()`，排序键仅允许引用非 Geometry 的简单字段编码；空排序键显式使用 `tuple()`。
- Geometry 受管建表支持已安装 PostGIS 的 PostgreSQL、MySQL 8.x InnoDB 和单机 ClickHouse，必须使用 EPSG + XY，且不创建空间索引。PostGIS/MySQL 在查看 DDL、建表和导入预览时通过目标连接校验运行能力以及 EPSG 到本地 SRID/SRS ID 的唯一映射；ClickHouse 使用 `String` / `Nullable(String)` 保存原始 WKB，并在列 comment 中写入固定 marker。
- `EXTERNAL` 模式下，用户可从任意已启用 JDBC 数据源的默认命名空间选择一张普通表，不限制 SOURCE、STORAGE 或 DISTRIBUTION 用途。创建或更换绑定表时，服务端在管理数据库事务外读取并映射元数据，再使用短事务保存模型和导入字段；系统绝不修改该表。
- `EXTERNAL` 模式的物理字段以数据库为准：字段编码、类型、长度/精度、是否可空和主键不可在平台修改，只允许调整字段名称、说明、展示排序和关联码表。若物理表字段类型或参数无法映射到当前模型契约，创建会被拒绝并指出字段。
- `MANAGED` 和 `EXTERNAL` 字段都可以绑定一个兼容码表。绑定只增加一次模型 Schema 版本，不进入 DDL、结构指纹或物理变更计划；码表停用后保留已有绑定并允许解除。完整规则见[树形码表管理](standard-dictionary-management.md)。
- 当前模型契约的物理标识符统一采用小写字母、数字和下划线；外部表或字段名称不符合该规则时会明确拒绝导入，避免后续任务或服务引用错误列名。
- 发布要求至少存在一个字段、关联 JDBC 数据源存在且已启用，并且实时结构检查结果为 `MATCHED`。发布本身不执行 DDL。
- 已发布模型的物理位置和字段定义锁定，名称、目录和说明仍可修改。
- 数仓分层及其前缀、允许输入规则属于业务组织和规划元数据，草稿、已发布和已停用模型都可调整；它不改变模型 Schema 版本或物理表定义。
- 已创建且包含 Geometry 的匹配受管表在 V1 不生成物理结构变更计划；字段仅允许修改显示名称、说明和展示顺序。
- 已发布模型可以停用，重新启用前会重新检查物理表结构。
- 已发布模型不能直接删除，需先停用。删除只删除模型和字段元数据，不删除物理表。

`ModelPhysicalTablePort` 是模型编排与 JDBC 实现的显式边界。方言模块接收数据库无关的建表定义，负责标识符引用、字段类型映射、结构比较和受控 SQL 渲染；业务模块不拼接 SQL。

结构检查默认采用严格匹配：字段名、类型、字符串长度、小数精度/小数位、是否可空、主键字段及顺序必须一致；字段顺序和普通注释不参与比较。ClickHouse 额外校验 `MergeTree` 引擎和排序键，但其物理 `String` 不保存模型字符串长度，也没有关系型唯一主键约束；这些差异由 ClickHouse 方言明确归一。ClickHouse Geometry 的保留 marker 属于结构声明，encoding、kind、CRS、dimension 或 nullable 任一不一致都会导致结构不匹配。差异会按缺少字段、多余字段、类型、长度、精度、空值约束、主键和存储配置返回。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/models` | 使用统一 Search DSL 分页查询模型 |
| `GET` | `/api/v1/models/{id}` | 查询模型及字段详情 |
| `GET` | `/api/v1/models/{id}/related-tasks` | 使用统一 Search DSL 分页查询最后保存任务定义中引用当前模型的任务；可按 `INPUT/OUTPUT` 角色过滤 |
| `GET` | `/api/v1/models/platform-types` | 查询指定数据存储支持的平台字段类型 |
| `GET` | `/api/v1/models/external-table-import-preview` | 预览已有表导入后的平台字段和映射质量 |
| `POST` | `/api/v1/models/managed-import-preview` | 将任意已启用 JDBC 源表映射为目标数据存储上的受管模型字段候选；不保存模型、不执行 DDL |
| `POST` | `/api/v1/models/managed-drafts` | 原子保存 `MANAGED + DRAFT` 模型及已校对字段；要求目标物理表尚不存在 |
| `GET` | `/api/v1/models/metadata-import-template` | 下载固定版本的空白 `.xlsx` 模型元数据模板 |
| `POST` | `/api/v1/models/actions/preview-metadata-import` | 解析并校验 `.xlsx` 模型元数据；只读预览，不保存模型、不执行 DDL |
| `POST` | `/api/v1/models/actions/export-metadata` | 将选中的 `MANAGED` 模型及字段元数据导出为 `.xlsx`；`EXTERNAL` 暂不支持 |
| `GET` | `/api/v1/models/{id}/physical-table` | 实时检查目标物理表和结构差异 |
| `GET` | `/api/v1/models/{id}/physical-table/ddl` | 预览平台生成的建表 SQL |
| `GET` | `/api/v1/models/{id}/data-preview` | 快速预览最多 50 行数据；不接收筛选、排序、分页参数，也不执行总数统计 |
| `POST` | `/api/v1/models/{id}/actions/query-data` | 只读条件查询；请求体支持字段选择、AND/OR 顶层条件、筛选、最多 3 个排序、分页和可选总数 |
| `POST` | `/api/v1/models` | 新建草稿模型 |
| `POST` | `/api/v1/models/{id}/actions/update` | 修改模型元数据 |
| `POST` | `/api/v1/models/{id}/actions/update-fields` | 整体保存字段定义 |
| `POST` | `/api/v1/models/{id}/actions/create-physical-table` | 创建 `MANAGED` 物理表并再次校验 |
| `POST` | `/api/v1/models/{id}/actions/publish` | 校验物理表后发布模型 |
| `POST` | `/api/v1/models/{id}/actions/disable` | 停用模型 |
| `POST` | `/api/v1/models/{id}/actions/enable` | 重新启用模型 |
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

## 前端交互

模型页复用通用 `MODEL` 目录树，采用左树右表的紧凑布局。列表支持名称/编码、状态、数仓分层、JDBC 数据源和目录筛选，表格占满剩余高度并在表体内部滚动。数仓分层在“系统管理 / 数仓分层”中动态维护，不与模型目录层级相互替代：目录表达资产归属和导航，数仓分层表达数据加工阶段。

新建和修改使用紧凑抽屉，可选一个启用的数仓分层，并只选择 JDBC 数据源和物理表名，不重复填写 Catalog 或 Schema。普通新建时，如果模型编码仍为空，选择分层会填入其前缀；切换分层只会替换仍等于旧自动前缀的编码，人工输入永不覆盖。模型基本信息展示当前分层的编码规范，但不判断现有编码是否合规。编辑引用停用分层的模型时允许原值保留或清空，但不能把其他模型新分配到停用分层。`MANAGED` 模式只列出具有数据存储用途的数据源；选择“绑定已有表”后，列表扩展为全部已启用的 JDBC 数据源，并从默认命名空间加载普通表、展示字段预览和导入类型；存在不支持字段时不能保存。新建完成后直接进入独立模型详情页的字段工作区。

模型列表另提供“从数据源导入”入口，用于把多张 JDBC 普通表的一次性结构快照复制为多个 `MANAGED + DRAFT` 模型。来源可以是任意已启用 JDBC 数据源且不限制业务用途；一个批次统一选择具有 `STORAGE` 用途的目标 JDBC 数据源、可选目录和可选数仓分层，批量分层用于初始化草稿，每个模型仍可覆盖或清空，且不会从来源表推断分层。草稿模型编码候选为“最终所选分层前缀 + 小写源表名”，源表名已有相同前缀时不重复添加；非法、超长或批次内重复候选仍留空标红。批量或单模型切换分层只更新未被人工修改的编码。每个模型可以单独修改目标物理表名。预览会依次执行源物理类型到平台类型、平台类型到目标物理类型的安全映射，并带入字段编码、名称、长度/精度、可空、主键、顺序和注释；不合法标识符或不安全类型保持未解决状态，由用户补齐后再提交。默认值、自增、生成列和索引等当前模型无法表达的信息只展示警告。每个模型及其字段原子保存，批量请求最多三个并发且允许部分成功；整个导入过程不绑定源表、不导入数据，也不执行建表 DDL，用户调整字段后仍需在详情页显式创建物理表。普通新建中的 `EXTERNAL` 模式继续负责绑定已有表，两种语义互不替代。Excel 导入始终保留文件中的模型编码，不按分层前缀自动改写。

模型详情的“关联任务”页签查询任务最后一次成功保存的定义。Local SQL 输入来自显式输入模型表，输出来自定义中的输出模型；Canvas 只读取 `MODEL_INPUT/MODEL_OUTPUT` 引用索引，JDBC、Kafka、文件和 HTTP 节点不产生模型关系。同一任务多次引用当前模型时按任务聚合，保留全部节点位置和 `INPUT/OUTPUT` 角色。该能力不保存新的关系实体、不分析 SQL 文本，也不表示历史运行血缘。接口同时要求 `model.view` 和 `task.view`，前端在缺少任务查看权限时不显示该页签。

### Excel 模型元数据交换

Excel 能力只交换平台已经持久化的表结构元数据，不是环境备份，也不读取或写入物理表数据。第一版只接受系统模板生成的 `.xlsx` 文件；导入结果固定为 `MANAGED + DRAFT`，导出只允许 `MANAGED` 模型。文件不包含数据源连接、数据源或目录 UUID、Catalog、Schema、模型或字段 UUID、生命周期状态、来源绑定和审计时间。导入时在界面统一选择一个已启用且具有 `STORAGE` 用途的目标 JDBC 数据源和可选模型目录，因此同一文件可以跨环境、跨受支持的数据库方言使用。

当前写出格式为 V4，继续兼容导入 V1、V2 和 V3。四个版本均固定包含三个工作表：

| 工作表 | 固定内容 |
| --- | --- |
| `说明` | 文件标识 `DATASCALPEL_MODEL_METADATA`、格式版本、填写规则和支持的平台字段类型 |
| `模型` | V1/V2 包含模型编码、模型名称、目标物理表名、模型说明、可选 ClickHouse 排序键；V3/V4 在其中增加数仓分层编码和展示名称 |
| `字段` | V1 包含所属模型编码、字段编码、字段名称、平台字段类型、长度、精度、小数位、可空、主键、排序值和字段说明；V2/V3 追加几何类型、CRS Authority、CRS Code、坐标维度；V4 再追加码表编码 |

字段类型使用 `PlatformDataType`，不在文件中保存 MySQL、PostgreSQL 等数据库原生类型。V2—V4 Geometry 保存平台 kind、EPSG code 和 XY，不保存本地 SRID/SRS ID 或 ClickHouse WKB marker。V3/V4 导入只按大写化后的分层编码匹配当前启用定义，分层名称仅用于导出展示；空编码表示未分层，未知、非法或停用编码会在校对页标红，用户可以改选启用分层或清空。V1/V2 一律按未分层导入。V4 的码表编码同样大写化后匹配当前启用定义，并验证字段类型、长度、精度、范围和小数位能否安全表达全部码表值；未知、停用或不兼容码表保持未解决，用户可改选兼容码表或清空。V1—V3 缺少该列时按未绑定处理。预览会使用目标方言验证平台类型能否安全映射；`LOSSY` 或 `UNSUPPORTED` 映射保持未解决并阻止对应模型创建。ClickHouse 排序键以逗号分隔的字段编码保存，Geometry 字段不能作为排序键；导入到非 ClickHouse 目标时忽略并给出警告。默认值、自增、生成列和索引因当前模型契约无法表达，不进入文件，也不会伪造成其他配置。

解析阶段不保存任何实体。文件级结构问题（模板标识、版本、工作表或表头错误）直接拒绝；模型和字段内容问题在校对页标红，用户可以修改模型信息并通过字段弹窗补齐空值。主键字段不得可空，字段编码在模型内必须唯一，排序值必须是大于等于零的整数，目标模型编码和物理位置不得冲突，目标物理表必须能够实时确认不存在。单文件上限为 10 MB、200 个模型、每模型 500 个字段和总计 20,000 个字段，并拒绝模板数据单元格中的公式。

校对完成后继续复用 `/managed-drafts` 逐项创建，最多三个并发；每个模型和字段在一个短事务内原子保存，单项失败不回滚其他成功项，失败项可保留调整内容后重试。预览、导入草稿和导出均不调用物理表创建端口；只有用户随后在模型详情页查看 DDL、二次确认并调用 `/actions/create-physical-table` 时才执行建表。

导出入口包括列表选中项、列表单模型操作和模型详情页。列表中的 `EXTERNAL` 模型不可选择，服务端也会拒绝任何包含 `EXTERNAL` 的导出请求。导出的文件与空白模板同构，可以编辑后重新导入；重新导入始终执行“创建新草稿”，不会通过编码更新或覆盖已有模型。

详情页采用 Tab 结构，第一版包含基础信息、字段信息、数据查询、关联任务和血缘分析五个工作区。受管模型的字段工作区支持新增、从启用的常用字段模板复制、修改、删除和整体保存；复制时会跳过同编码字段及目标存储不支持的类型，停用码表绑定留空，所有降级均明确提示。外部表模型只开放字段名称、说明、排序和关联码表；模型发布后全部转为只读。

基础信息、字段信息、数据预览和关联任务使用真实接口。基础信息展示物理表来源、实时状态、建表 SQL 和结构差异；仅受管表显示创建物理表操作。数据预览只在物理表结构匹配时读取真实数据，并分为两个模式：快速预览固定最多读取 50 行；条件查询可选择返回字段、维护多个筛选和最多 3 个排序，并按 20、50、100 条分页。条件查询默认不执行 `count(*)`，而是多取一行判断下一页；单条查询超时 15 秒、最多 20 个筛选条件、最大分页偏移 10,000。所有字段名都由模型字段白名单解析为受控物理列，二进制和 Geometry 字段默认不返回，也不能显式返回、筛选、排序、分组或聚合，绝不接收任意 SQL。未定义排序且无主键/ClickHouse 排序键时，页面会提示翻页顺序可能不稳定。关联任务读取任务最后一次成功保存的定义；血缘分析仍使用明确标识的前端 Mock 数据，并通过 X6 提供方向、深度、缩放和节点检查交互。

列表的筛选、目录、分页和每页数量保存在 URL 查询参数中。从模型名称或字段入口进入详情后，返回列表会恢复之前的查询上下文。
