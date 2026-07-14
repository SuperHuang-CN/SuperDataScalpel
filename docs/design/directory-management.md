# 通用目录管理

## 目标与边界

目录是多个业务域共享的轻量分类树，而不是独立的资源归属、权限或数据隔离体系。当前已经启用数据源和模型目录；后续数据服务沿用同一套目录能力，但各自使用独立的目录范围。

业务实体只保存 `UUID directoryId` 标量字段，不建立 JPA Entity 关联。目录名称、路径等展示数据不冗余到业务实体中。

## 数据模型

表：`ds_directory`

| 字段 | 含义 |
| --- | --- |
| `id`、`created_at`、`updated_at` | 继承 `BaseEntity` 的 UUID 和审计时间 |
| `scope` | 目录范围：`DATA_SOURCE`、`MODEL`、`DATA_SERVICE` |
| `parent_id` | 上级目录 UUID；为空表示顶级目录 |
| `name` | 同一范围、同一上级下唯一的目录名称 |
| `sort_order` | 同级显示排序，数值越小越靠前 |
| `description` | 可选说明 |

目录只允许引用相同 `scope` 的父目录；移动目录时禁止移动到自身或其子树下。删除时若存在子目录或被当前范围内的业务数据引用，则返回冲突，不做级联删除。

## 统计与筛选

目录树接口为每个节点返回：

- `directResourceCount`：直接归属此目录的数据数。
- `resourceCount`：此目录及其所有子目录的累计数据数。

数据源和模型目录树分别按各自业务表的 `directory_id` 聚合直属数量，再在内存中自底向上累加；不会维护易失真的冗余计数器。点击父目录时，业务页面使用当前目录及全部子目录的 UUID 组合为统一 Search DSL 条件，因此列表与 `resourceCount` 含义一致。

“全部”和“未分类”是前端虚拟节点，不落库；未分类对应 `directoryId:null`。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/directories?scope=DATA_SOURCE` | 查询指定范围的目录树和统计 |
| `GET` | `/api/v1/directories/{id}` | 查询目录详情 |
| `POST` | `/api/v1/directories` | 创建目录 |
| `POST` | `/api/v1/directories/{id}/actions/update` | 修改目录名称、上级、排序和说明 |
| `POST` | `/api/v1/directories/{id}/actions/delete` | 删除空目录 |

目前数据源和模型的创建、修改请求与响应均包含可选的 `directoryId`。服务端分别校验目录存在且范围为 `DATA_SOURCE` 或 `MODEL`。

## 本地样例数据

仅在 `local` Profile 下，系统启动时会以“缺失才创建”的方式准备：

- `示例数据源 / 业务系统`、`示例数据源 / 数据仓库` 两级目录；
- 三条示例数据源，其中两条已归类、一条未分类。

本地初始化的演示连接仍可能使用占位地址；数据源连接测试现已访问真实数据库，因此测试演示连接时预期会返回明确的连接失败结果。
