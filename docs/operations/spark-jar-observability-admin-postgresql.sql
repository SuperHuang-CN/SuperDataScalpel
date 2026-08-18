-- Spark JAR 用户作业轻量可观测能力 v1：Admin PostgreSQL 增量脚本。
-- 只增加可空列，不重建、不清空现有 task_run 数据；可重复执行。

ALTER TABLE task_run
    ADD COLUMN IF NOT EXISTS user_job_phase varchar(100),
    ADD COLUMN IF NOT EXISTS user_job_status_message varchar(1000),
    ADD COLUMN IF NOT EXISTS user_job_status_at timestamptz,
    ADD COLUMN IF NOT EXISTS user_job_metrics text;
