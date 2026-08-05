# 文件数据集异步解析阶段五：运维

## 调度与配置

所有格式共享解析调度器。业务系统配置包括：

- `file-dataset.parsing.queue-enabled`
- `file-dataset.parsing.worker-concurrency`
- `file-dataset.parsing.max-attempts`
- `file-dataset.parsing.retry-base-delay-seconds`
- `file-dataset.parsing.history-retention-days`

部署级安全参数继续通过 YAML 或环境变量配置。关闭队列只停止领取，不取消已经排队或运行的 Job。

## 清理

终态 Job 按历史保留天数清理。业务对象没有延迟清理器：

- 失败临时文件立即删除；
- 覆盖、替换和删除提交后立即删除无引用旧对象和物化目录；
- 删除失败记录告警，由技术人员检查 MinIO 孤儿对象。

MinIO Bucket 必须关闭版本管理和 Object Lock。Bucket 生命周期策略只负责平台运行产物和意外
孤儿的兜底，不用于保存文件数据集历史。

## 部署

升级前停止 Admin、Worker、Task Engine 和 Dispatcher，排空或取消旧 Manifest 任务，清空
`data-scalpel/file-datasets/` 前缀并执行破坏性重建 SQL。Task Engine 与 Admin 同步升级并
使用 Manifest v8。

## 监控

监控重点包括队列深度、最老等待时间、运行任务数、租约过期恢复、失败率和对象删除告警。业务
数据问题通过 Job 名称快照和错误摘要排查。
