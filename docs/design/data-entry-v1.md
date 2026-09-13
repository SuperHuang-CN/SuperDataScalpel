# 数据填报 V1

## 1. 模块边界

数据填报复用现有 `DataModel`、`DataModelField` 和模型物理表，不创建第二套模型。所有业务实现位于
`data-scalpel-business` 的 `dataentry` 包，依赖模型、数据源、码表和方言已有公开能力；模型、码表的实体与生命周期不反向依赖数据填报。

填报对模型、字段、来源模型和码表均使用 UUID 标量弱引用，不建立 JPA Entity 关联或数据库外键。模型或来源资源发生删除、停用或结构变化时，填报表单继续存在，由实时健康检查报告问题。删除模型、字段、码表或码项时不检查填报引用。

## 2. 管理库对象

- `ds_data_entry_form`：保存目标 `model_id`、`DRAFT / PUBLISHED / DISABLED` 状态和最近发布确认的模型结构版本；`model_id` 唯一。
- `ds_data_entry_model_lookup`：只保存表单、目标字段、来源模型和来源标签字段的 UUID；来源 value 固定为来源模型唯一的单字段业务主键。
- `ds_data_entry_operation_log`：记录 `INSERT / UPDATE / IMPORT / DELETE` 的 `PROCESSING / SUCCEEDED / PARTIALLY_SUCCEEDED / FAILED` 状态、操作人、数量、安全请求快照和脱敏错误。
- `ds_data_entry_record_change`：按记录保存操作日志、表单、稳定记录标识、批次序号、操作类型、执行状态、操作人、业务主键、字段定义、提交值及实际前后值快照。字段快照保留当时的字段 UUID、编码、名称和平台类型。

记录标识使用“表单 UUID + 按字段 UUID 排序的规范化业务主键”计算 SHA-256，结构化主键同时单独保存，避免字符串拼接歧义。同一业务主键删除后重新新增时历史连续；业务主键定义变化后使用新身份。删除物理记录不删除历史；删除草稿或已停用表单时，管理库事务依次删除记录变更、关联下拉配置、操作日志和表单。目标物理表数据永不随表单删除。

## 3. 生命周期与健康

创建表单只要求模型存在且尚无表单。草稿和已停用表单可以整体替换关联模型下拉配置。发布会执行完整实时检查，并将当时的模型 `schemaVersion` 写入表单。已发布表单可直接停用，即使模型已经失效；已发布表单不能直接删除。

完整健康检查区分五种能力：

- `canPublish`：模型已发布、受管，数据源为启用的 PostgreSQL、HighGo、人大金仓、openGauss、MySQL 或单机 ClickHouse 存储，字段与业务主键有效，物理表匹配，码表和关联下拉有效。
- `canSubmit`：在发布条件上要求表单为已发布且模型结构版本仍与发布版本相同。
- `canUpdateEntries`：要求表单已发布、模型版本一致、目标数据库/物理表/业务主键健康，且数据库为 PostgreSQL、HighGo、人大金仓、openGauss 或 MySQL；码表或关联来源整体失效不阻止保留已有值，实际修改对应字段时再校验新值。
- `canDeleteEntries`：要求表单已发布、模型版本一致、目标数据库/物理表/业务主键健康；不受码表或关联下拉失效影响。
- `canQueryEntries`：草稿和停用表单也可查询，只要求现有模型查询能力能够访问匹配的物理表；外部模型和非填报支持数据库也可用于排查性只读查询。

模型字段的 `primaryKey` 标记是填报业务主键，可以是单字段或联合字段。PostgreSQL/MySQL 通常同时具有物理主键约束；ClickHouse 只保存模型元数据，`MergeTree` DDL 和物理结构检查不要求关系型主键约束。完整检查使用业务主键分组检查目标表和关联来源是否存在重复值，分别报告 `TARGET_BUSINESS_KEY_DUPLICATE` 和 `LOOKUP_SOURCE_BUSINESS_KEY_DUPLICATE`，阻止写入和删除但不阻止只读查询。

列表只进行管理库元数据诊断，不触发外部 JDBC。详情、健康、发布、新增、编辑和删除执行完整实时检查。问题使用稳定代码，例如 `TARGET_MODEL_MISSING`、`TARGET_SCHEMA_CHANGED`、`TARGET_TABLE_NOT_MATCHED`、`TARGET_BUSINESS_KEY_DUPLICATE`、`TARGET_UPDATE_UNSUPPORTED`、`DICTIONARY_INVALID` 和各类 `LOOKUP_*` 问题。

## 4. 数据操作

新增请求必须恰好包含模型全部字段编码。填报请求使用局部 Jackson 反序列化器拒绝重复 JSON 字段；未知或缺少字段也会拒绝。字段值统一由 `PlatformQueryValueConverter` 严格转换，空字符串作为合法 STRING 保留。码表值必须存在且当前有效；关联模型值必须能够按来源模型唯一业务主键查到。

物理新增使用可信模型标识符、方言标识符引用和 `PreparedStatement` 参数，执行普通 `INSERT`，不使用 UPSERT。写入前按业务主键查询目标表，发现已有记录返回 409；并发提交或绕过填报直接写库仍可能产生竞态，后续由健康检查发现。

记录详情按完整业务主键重新读取全部字段，并返回记录级历史查询使用的稳定标识。列表查询始终附带业务主键字段，即使用户隐藏这些列。编辑请求必须携带完整业务主键和全部当前字段，拒绝重复 JSON 字段、未知字段和主键变化。目标库在一个短事务内执行“读取旧值 → 参数化 UPDATE → 读取实际新值”；业务主键零条或多条命中均拒绝。服务端不比较客户端版本，后保存的值生效。规范化后没有非主键字段变化时不执行 UPDATE，也不保存记录变更。

LONG、DECIMAL、日期时间在详情和编辑中保持字符串或精确平台值往返。已有下拉值会主动反查并展示有效、停用、缺失或来源不可用状态；未修改的失效值可以原样保留，修改后的码表或关联值必须重新通过当前校验。

批量删除一次最多 100 个 key，每个 key 必须恰好包含全部当前模型业务主键字段。规范化后的重复 key 在访问目标库前拒绝；执行前要求每个业务主键恰好命中一条。PostgreSQL、HighGo、人大金仓、openGauss 和 MySQL 使用一个参数化 `DELETE`，ClickHouse 使用 `ALTER TABLE ... DELETE WHERE ... SETTINGS mutations_sync = 2` 同步等待单机 Mutation 完成并复查。删除不承诺回滚。

管理库和目标库不组成分布式事务：

1. 管理库短事务写入 `PROCESSING` 操作日志和 `PREPARED` 逐记录变更；记录变更保存失败时不执行对应目标写入；
2. 管理库事务外执行健康检查和目标数据库操作；
3. 管理库新短事务写入操作汇总以及逐记录的 `SUCCEEDED / FAILED / UNKNOWN` 状态和实际快照。

新增和编辑完成后回读实际保存值，删除前读取完整旧记录。目标库已提交后若回读或最终历史更新失败，记录变更保留 `UNKNOWN` 或 `PREPARED`，操作日志保留 `PARTIALLY_SUCCEEDED` 或 `PROCESSING`，响应标记 `manualVerificationRequired=true`；拟提交值不能冒充实际生效快照，系统不自动重放。批量操作中途失败时已经生效的数据保留：已知没有产生变更的失败仍返回错误；已部分完成或无法确认结果时返回 `PARTIALLY_SUCCEEDED`、已确认数量和人工核对提示。历史只采集功能上线后经填报入口发生的写入，不补造旧日志，也不采集任务和外部 SQL 修改。

### 4.1 Excel/CSV 批量导入

批量导入复用单条新增的模型、类型、码表和关联来源校验，使用 `dataentry.submit` 权限。支持 `.xlsx` 和 UTF-8 `.csv`，单文件最多 50 MB、100000 条非空数据行。流程为“上传全量校验并预览 → 用户确认导入”：服务端不保存上传文件或预览会话，确认时客户端重新上传同一文件，并携带覆盖文件内容、表单、模型版本、字段、码表和关联配置的 SHA-256 `previewDigest`。

两种文件统一使用两行表头：第一行为字段中文名称，第二行为唯一匹配依据的字段编码，第三行开始为数据。导入必须恰好包含当前模型全部字段，但允许调整列顺序。空单元格表示 `null`，STRING 使用显式空字段或文本 `""` 表示空字符串。Excel 公式和错误单元格不执行；LONG/DECIMAL 使用数字单元格时拒绝，以免 Excel 有效数字精度造成静默失真。

预览会扫描完整文件，但响应只包含前 100 条数据和最多 200 条问题，同时返回总行数、有效/错误行数和实际问题总数。文件内单字段或联合业务主键按规范化值去重，并按最多 500 个键一批检查目标表；已存在的键返回 `IMPORT_PRIMARY_KEY_EXISTS`。码表元数据一次加载，关联来源值按最多 500 个去重值分批查询，避免逐行访问管理库或外部数据库。

确认导入会重新执行完整健康检查、摘要校验、目标业务主键检查和全文件校验。PostgreSQL、HighGo、人大金仓、openGauss、MySQL 和 ClickHouse 均使用参数化 `INSERT`，每 500 条执行并立即生效；每批先写入全量 `PREPARED` 逐记录历史，执行后分批回读已确认成功记录的实际值并更新状态，不把十万行历史放入内存。中途失败时停止后续批次，已成功批次不回滚。驱动可确认的成功数量写入日志，连接中断或超时等结果不明场景要求人工核对且不得自动重试。IMPORT 操作日志仍只保存文件名、格式、文件摘要、行数、字段编码和最多 20 条规范化样例；完整逐行内容由记录变更分页承载，不保存原文件。

## 5. 字段控件和选项

BOOLEAN、整数、字符串模式 LONG/DECIMAL、有限浮点数、STRING、DATE、TIMESTAMP 和 TIMESTAMP_NTZ 使用对应 Ant Design 控件。BINARY 和 GEOMETRY 阻止表单发布。

字段绑定现有码表时自动使用码表选项，不保存静态选项副本。层级码表显示完整名称路径；有效节点可新选择，停用节点可解析历史值并显示“已停用”，删除节点显示原值和“无匹配项”。

关联模型要求已发布的 PostgreSQL、HighGo、人大金仓、openGauss、MySQL 或单机 ClickHouse 模型、匹配的物理表、一个单字段业务主键和一个非空 STRING 标签字段。选项分页按标签模糊搜索，展示“标签（业务主键）”，保存业务主键标量。来源出现重复 value 时返回来源不可用，不静默选择任意记录。统一接口最多批量反查 100 个当前页已有值，并返回 `ACTIVE / DISABLED / MISSING / SOURCE_UNAVAILABLE`。

## 6. API

基础路径为 `/api/v1/data-entry-forms`：

- `GET /`、`GET /model-candidates`、`GET /{id}`、`GET /{id}/health`
- `POST /`、`POST /{id}/actions/update-lookups`
- `POST /{id}/actions/publish`、`POST /{id}/actions/disable`、`POST /{id}/actions/delete`
- `POST /{id}/actions/query-data`（只读结构化查询）
- `POST /{id}/entries/actions/query-detail`（按完整业务主键只读查询详情）
- `POST /{id}/entries/actions/update`（单条编辑）
- `POST /{id}/fields/{fieldId}/actions/query-options`（只读选项查询）
- `POST /{id}/entries`、`POST /{id}/entries/actions/delete-batch`
- `GET /{id}/entries/import-template?format=XLSX|CSV`
- `POST /{id}/entries/actions/preview-import`（只读 multipart 全量校验）
- `POST /{id}/entries/actions/import`（multipart 分批生效导入）
- `GET /{id}/operation-logs`、`GET /{id}/operation-logs/{logId}`
- `GET /{id}/record-changes`（按稳定记录标识或操作日志 UUID 分页）
- `GET /{id}/record-changes/{changeId}`

成功响应不使用统一包装层；写操作响应返回 `operationLogId`、请求/影响数量、`SUCCEEDED / PARTIALLY_SUCCEEDED` 状态以及人工核对提示。错误继续使用全局 RFC 9457 `ProblemDetail`。确认未产生数据变更的目标数据库超时返回 504，其他不可用错误返回 502，版本/状态/业务主键冲突返回 409。

## 7. 权限与前端

- `dataentry.view`：表单、健康、物理数据、记录详情、选项、操作日志和记录变更历史。
- `dataentry.manage`：创建、配置、发布、停用和删除表单。
- `dataentry.submit`：单条新增、单条编辑、模板下载、批量导入预览和确认导入。
- `dataentry.delete`：按业务主键批量删除数据。

前端路由为 `/data-entry` 和 `/data-entry/:id`。详情包含数据填报、数据列表、字段配置和操作日志。数据列表行提供详情和编辑操作，共用包含“当前记录”“变更历史”页签的抽屉；保存后刷新详情、列表和日志缓存并保留列表筛选条件。操作日志详情分页显示逐记录明细，可从删除操作打开已删除记录的历史。数据填报页签提供 Excel/CSV 实时模板下载、单文件上传、有限数据/问题预览和确认导入；部分成功时刷新数据和日志并提示人工核对。模型条件查询面板以 `DataModelDataQueryPanel` 从模型模块公开，数据填报只经模型 `index.ts` 复用，模型前端不依赖数据填报。

## 8. HighGo 边界

HighGo 使用独立 `HIGHGO` 数据源类型、`com.highgo:HgdbJdbc:6.2.5` 驱动、`com.highgo.jdbc.Driver`、`jdbc:highgo://` URL 和默认端口 5866。方言继承 PostgreSQL 主体并保留 HighGo 厂商身份，覆盖元数据、平台类型映射、受控建表与结构维护、查询、填报写入、UPSERT、增量读取和快照同步；空间能力取决于目标库扩展检测。模型、Canvas、Manifest、Runner、标准/SQL/脚本数据服务均保留 `HIGHGO`，GeoServer 空间发布不包含 HighGo。Manifest v28 增加该运行时数据库枚举，Admin、Task Engine 和 Service Engine 使用同一驱动版本。
