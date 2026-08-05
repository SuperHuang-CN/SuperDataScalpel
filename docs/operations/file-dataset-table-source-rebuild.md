# 文件数据表当前来源模型重建

本次升级采用“稳定逻辑表 + 有序当前来源”，文件数据集只保存当前有效数据。旧数据、解析任务和
旧 `tableId` 不迁移，必须执行一次破坏性重建。

## 操作步骤

1. 停止 Admin、解析 Worker、Task Engine 和 Dispatcher，并排空或取消旧协议的运行任务。
2. 如需环境级回退，整套备份 PostgreSQL 和 MinIO。
3. 确认 MinIO Bucket 已关闭版本管理和 Object Lock。
4. 清空 MinIO Bucket `data-scalpel` 下的 `file-datasets/` 前缀。数据库脚本不能删除对象。
5. 执行
   [`file-dataset-table-source-rebuild.sql`](../../data-scalpel-admin/src/main/resources/db/file-dataset-table-source-rebuild.sql)。
6. 启动 Admin。Hibernate `ddl-auto=update` 会按最新实体创建：
   `ds_file_dataset`、`ds_file_dataset_file`、`ds_file_dataset_table`、
   `ds_file_dataset_table_source`、`ds_file_dataset_field` 和
   `ds_file_dataset_parse_job`。
7. 重新创建文件数据集并上传文件。
8. 所有引用旧 `tableId` 的 Canvas 节点必须重新选择；Task Engine 接受 Manifest v8，并兼容不含空间能力的 v7 任务。

脚本保留系统配置、目录、模型、任务和其他业务表，并删除已经废弃的文件来源清理配置。脚本使用
`CASCADE` 清理数据库内对旧文件数据集表的直接依赖；如环境存在自定义视图或外键，执行前应先
检查。

## 回滚边界

应用不提供旧数据恢复。需要回退部署时只能整体恢复升级前的数据库和对象存储备份，不能混用
新旧结构。
