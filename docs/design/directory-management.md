# 通用目录管理

## 目标与边界

目录是多个业务域共享的轻量分类树，而不是独立的资源归属、权限或数据隔离体系。数据源、文件数据集、模型、任务和数据服务均沿用同一套目录能力，但各自使用独立的目录范围。

数据资产的业务领域同样复用该数据模型，使用 `ASSET` 范围形成独立的门户分类树。它表达用户在资产门户中发现数据的业务视角，与 `MODEL`、`DATA_SERVICE` 等范围中用于技术管理的目录相互独立，不自动映射或同步。

业务实体只保存 `UUID directoryId` 标量字段，不建立 JPA Entity 关联。目录名称、路径等展示数据不冗余到业务实体中。

## 数据模型

表：`ds_directory`

| 字段 | 含义 |
| --- | --- |
| `id`、`created_at`、`updated_at` | 继承 `BaseEntity` 的 UUID 和审计时间 |
| `scope` | 目录范围：`DATA_SOURCE`、`FILE_DATASET`、`MODEL`、`TASK`、`DATA_SERVICE`、`ASSET` |
| `parent_id` | 上级目录 UUID；为空表示顶级目录 |
| `name` | 同一范围、同一上级下唯一的目录名称 |
| `sort_order` | 同级显示排序，数值越小越靠前 |
| `description` | 可选说明 |

对于已有 PostgreSQL 管理库，应用启动时会幂等重建 `ds_directory_scope_check`，使其取值与当前
`DirectoryScope` 枚举保持一致。Hibernate `ddl-auto=update` 不会可靠更新既有字符串枚举的
CHECK 约束，因此增加 `ASSET` 等目录范围后不能只依赖自动建表；其他数据库不执行这段兼容 SQL。

目录只允许引用相同 `scope` 的父目录；移动目录时禁止移动到自身或其子树下。删除时若存在子目录或被当前范围内的业务数据引用，则返回冲突，不做级联删除。

## 统计与筛选

目录树接口为每个节点返回：

- `directResourceCount`：直接归属此目录的数据数。
- `resourceCount`：此目录及其所有子目录的累计数据数。

资源目录树分别按各自业务表的 `directory_id` 聚合直属数量，再在内存中自底向上累加；不会维护易失真的冗余计数器。点击父目录时，业务页面使用当前目录及全部子目录的 UUID 组合为统一 Search DSL 条件，因此列表与 `resourceCount` 含义一致。

`ASSET` 范围按资产登记表的 `directory_id` 统计业务领域下的直属资产与后代资产数量。任何状态的资产记录都会计入统计；领域被资产引用时禁止删除，资产下线不会解除引用，只有调整所属领域或删除资产记录后才可删除该领域。

“全部”和“未分类”是资源管理页面的前端虚拟节点，不落库；未分类对应 `directoryId:null`。`ASSET` 业务领域维护页不展示这两个虚拟节点。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/directories?scope=DATA_SOURCE` | 查询指定范围的目录树和统计；`scope` 也可为 `FILE_DATASET`、`MODEL`、`TASK`、`DATA_SERVICE`、`ASSET` |
| `GET` | `/api/v1/directories/{id}` | 查询目录详情 |
| `POST` | `/api/v1/directories` | 创建目录 |
| `POST` | `/api/v1/directories/{id}/actions/update` | 修改目录名称、上级、排序和说明 |
| `POST` | `/api/v1/directories/{id}/actions/delete` | 删除空目录 |
| `GET` | `/api/v1/directories/actions/download-import-template` | 下载通用目录导入模板 |
| `GET` | `/api/v1/directories/actions/export?scope=MODEL` | 导出指定范围的完整目录树 |
| `POST` | `/api/v1/directories/actions/import?scope=MODEL` | 上传 `.xlsx` 并原子导入指定范围的目录树 |

各类目录化资源的创建、修改请求与响应包含可选的 `directoryId`。资产登记记录使用 `ASSET` 领域，并在编辑和发布时校验领域存在且范围匹配。

## 批量导入导出

导入和导出是目录范围级操作，统一放在公共目录面板头部，不在每个节点重复增加按钮。查看权限可以下载模板和导出，管理权限可以导入；同一实现覆盖数据源、文件数据集、模型、任务、数据服务和资产业务领域。

Excel 固定包含“说明”和“目录”两个工作表。“目录”列为 `行标识*`、`父行标识`、`目录名称*`、`排序`、`说明`。行标识只在当前文件内使用，父行标识为空表示顶级目录，因此模板可以表达任意深度且不依赖数据库 UUID，目录范围由导入页面和接口参数决定。

系统先校验整份文件的格式版本、父行引用、循环引用、同级重名、字段长度、文件大小和行数，通过后在一个管理库事务中合并。同一父目录下按名称忽略大小写匹配：不存在则新增，已存在则更新名称大小写、排序和说明；导入不会删除文件中未出现的现有目录。单个文件最多 5000 条目录、10 MB，只支持 `.xlsx`。

## 本地初始化

系统不再自动创建示例目录或数据源。本地环境中的目录和连接均由使用者按实际项目需要创建；这避免启动时重新出现不可用的演示连接。
