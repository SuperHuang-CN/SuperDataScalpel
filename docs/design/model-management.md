# 模型管理第一版

## 定位与范围

模型是任务和数据服务后续可以稳定引用的结构契约。一个模型绑定一个具有“数据存储”用途的数据源，并记录预期的 Catalog、Schema、物理表名和字段定义。

第一版只管理模型元数据和交互，不连接模型所绑定的数据存储，不创建、修改、校验或删除物理表，也不读取物理表数据。指标管理不在本阶段范围内；指标以后作为独立业务能力通过模型和字段 UUID 引用已发布模型。

## 数据模型

表 `ds_data_model`：

| 字段 | 含义 |
| --- | --- |
| `id`、`created_at`、`updated_at` | 继承 `BaseEntity` 的 UUID 和审计时间 |
| `code` | 全局唯一且创建后不可修改的模型编码 |
| `name` | 模型显示名称 |
| `directory_id` | 可选的 `MODEL` 范围目录 UUID |
| `storage_data_source_id` | 具有数据存储用途的数据源 UUID |
| `catalog_name`、`schema_name`、`physical_table_name` | 预期物理位置，仅作为元数据保存 |
| `status` | `DRAFT`、`PUBLISHED`、`DISABLED` |
| `description` | 模型说明 |

同一数据存储、Catalog、Schema 和物理表名组合只能被一个模型使用。删除被模型引用的数据源会返回冲突。

表 `ds_data_model_field`：

| 字段 | 含义 |
| --- | --- |
| `id`、`created_at`、`updated_at` | UUID 和审计时间 |
| `model_id` | 模型 UUID 标量引用 |
| `code`、`name` | 字段编码和显示名称 |
| `field_type` | 数据库无关的字段类型 |
| `field_length` | 字符串长度 |
| `numeric_precision`、`numeric_scale` | 小数精度与小数位 |
| `nullable`、`primary_key` | 是否允许为空、是否为主键字段 |
| `sort_order` | 字段展示和未来建表顺序 |
| `description` | 字段说明 |

第一版字段类型包括字符串、长文本、整数、长整数、小数、布尔、日期、日期时间和二进制。模型字段使用独立 UUID；整体保存字段时会保留仍存在字段的 UUID，为以后任务、服务和指标引用提供稳定标识。

## 生命周期

- 新建模型默认为 `DRAFT`，可以修改物理位置元数据和字段定义。
- 发布要求至少存在一个字段、关联数据存储存在且已启用。发布只将状态改为 `PUBLISHED`，不会执行 DDL。
- 已发布模型的物理位置和字段定义锁定，名称、目录和说明仍可修改。
- 已发布模型可以停用，停用后可以重新启用；这两个动作也只更新元数据状态。
- 已发布模型不能直接删除，需先停用。删除只删除模型和字段元数据，不删除物理表。

`ModelPhysicalTablePort` 是后续物理表阶段的显式边界。第一版使用 `MetadataOnlyModelPhysicalTablePort`，其发布方法为空实现。未来需要在用户确认 DDL 规则后替换该实现，而不改变模型接口和页面交互。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/models` | 使用统一 Search DSL 分页查询模型 |
| `GET` | `/api/v1/models/{id}` | 查询模型及字段详情 |
| `POST` | `/api/v1/models` | 新建草稿模型 |
| `POST` | `/api/v1/models/{id}/actions/update` | 修改模型元数据 |
| `POST` | `/api/v1/models/{id}/actions/update-fields` | 整体保存字段定义 |
| `POST` | `/api/v1/models/{id}/actions/publish` | 发布元数据 |
| `POST` | `/api/v1/models/{id}/actions/disable` | 停用模型 |
| `POST` | `/api/v1/models/{id}/actions/enable` | 重新启用模型 |
| `POST` | `/api/v1/models/{id}/actions/delete` | 删除模型元数据 |

## 前端交互

模型页复用通用 `MODEL` 目录树，采用左树右表的紧凑布局。列表支持名称/编码、状态、数据存储和目录筛选，表格占满剩余高度并在表体内部滚动。

新建和修改使用紧凑抽屉；新建完成后直接进入字段工作区。字段工作区支持新增、修改、删除和整体保存，模型发布后转为只读。详情抽屉始终明确展示“元数据模式”，避免用户误认为系统已经操作了物理表。
