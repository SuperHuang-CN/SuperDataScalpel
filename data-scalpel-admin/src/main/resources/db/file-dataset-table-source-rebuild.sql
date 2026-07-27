-- 文件数据集“仅当前数据”模型的一次性破坏性重建脚本（PostgreSQL）。
--
-- 使用前：
-- 1. 停止 Admin、文件解析 Worker、Task Engine 和 Dispatcher；
-- 2. 确认无需保留现有文件数据集、旧 tableId 和 Canvas 文件输入选择；
-- 3. 备份数据库，并清空 MinIO data-scalpel Bucket 中的 file-datasets/ 前缀；
-- 4. 确认该 Bucket 已关闭版本管理和 Object Lock；
-- 5. 执行本脚本后重新启动 Admin，由 ddl-auto=update 创建最新表结构；
-- 6. 重新创建文件数据集并上传文件，Canvas 中重新选择逻辑表。
--
-- 系统配置、目录、任务和其他业务表不会被本脚本删除。CASCADE 只用于移除数据库中
-- 直接依赖这些文件数据集表的约束/视图；执行前应先检查自定义数据库对象。

BEGIN;

DROP TABLE IF EXISTS ds_file_dataset_source_usage CASCADE;
DROP TABLE IF EXISTS ds_file_dataset_parse_job CASCADE;
DROP TABLE IF EXISTS ds_file_dataset_field CASCADE;
DROP TABLE IF EXISTS ds_file_dataset_table_source CASCADE;
DROP TABLE IF EXISTS ds_file_dataset_table CASCADE;
DROP TABLE IF EXISTS ds_file_dataset_file CASCADE;
DROP TABLE IF EXISTS ds_file_dataset CASCADE;

DELETE FROM sys_configuration
WHERE config_key = 'file-dataset.source.retired-retention-hours';

COMMIT;
