# 文件数据集统一异步解析队列

## 1. 职责

所有文件类型共用一套数据库任务队列。上传接口完成输入校验、对象保存、业务记录和 Job 入队后
立即返回，Worker 在后台完成：

- `FILE_PREPARATION`：SHP/GDB 归档检查、物化和表发现；
- `TABLE_SOURCE_VALIDATE`：初始、追加或覆盖来源的完整 Schema 校验。

`FileDatasetParseJob` 表达执行历史和重试，不表达业务数据版本。
`FileDatasetTableSource` 只在校验成功后创建，只包含当前有效来源。

## 2. 入队与串联

普通单对象格式上传后直接创建逻辑表和 `INITIAL` 校验 Job。SHP/GDB 先创建准备 Job：

1. 准备成功后把文件置为 `READY`；
2. 为发现的表创建逻辑表和 `INITIAL` 校验 Job；
3. SHP 表级追加或覆盖的准备 Job 成功后，向同一逻辑表交接为校验 Job；
4. Excel/GDB 不开放表级追加和覆盖。

一张表只允许一个 `currentLoadJobId`。同一操作从准备 Job 交接到校验 Job 时原子替换该 ID。

## 3. 状态、租约和恢复

Job 状态为 `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED`。Worker 使用 PostgreSQL 行锁领取最早
可执行任务，持有有期限的执行租约并发送心跳：

1. 短事务领取 Job 并核对当前 Job ID；
2. 事务外读取对象、准备或完整校验；
3. 心跳延长租约；
4. 短事务再次核对租约和当前 Job ID后提交结果。

对象存储、网络等瞬时错误按指数退避自动重试；格式、归档安全、编码、Schema 和数据值错误直接
终态失败。租约过期后恢复器重新排队；达到最大尝试次数后失败并执行相同的临时数据清理。

## 4. 过期保护与失败收敛

- 表结果必须满足 `table.currentLoadJobId == job.id`。
- 文件准备结果必须满足 `file.currentPreparationJobId == job.id`。
- 不匹配结果不改变业务状态，未发布的物化目录由 Worker 丢弃，无引用临时文件立即清理。
- `INITIAL` 最终失败删除空逻辑表、字段和无引用文件。
- `APPEND/REPLACE_ALL/REPLACE_SOURCE` 最终失败只清理新文件，当前表和来源不变。
- 文件准备最终失败删除文件记录、原始对象和物化目录。
- 多表 Excel/GDB 文件只有在没有当前来源和其他非终态 Job 时才删除。

排队任务可在删除或替换时取消；运行任务持有执行租约，管理操作返回 `409`，避免后台读写与管理
事务相互踩踏。

## 5. 系统配置

| 配置键 | 默认值 | 作用 |
| --- | ---: | --- |
| `file-dataset.parsing.queue-enabled` | `true` | 是否领取新任务 |
| `file-dataset.parsing.worker-concurrency` | `2` | 全格式共享并发 |
| `file-dataset.parsing.max-attempts` | `3` | 新 Job 最大尝试次数 |
| `file-dataset.parsing.retry-base-delay-seconds` | `30` | 指数退避基础秒数 |
| `file-dataset.parsing.history-retention-days` | `30` | 终态 Job 历史保留天数 |

关闭队列只停止领取，不取消现有任务。并发在下一轮调度生效；最大尝试次数在 Job 入队时保存；
历史清理只删除过期的终态 Job。

部署级全量校验上限使用
`data-scalpel.file-parsing.max-validated-uncompressed-size`，不与抽样预览上限混用。

## 6. 监控与验收

解析队列抽屉展示类型、状态、装载方式、目标来源、数据集/表/文件名称快照、尝试次数、可执行时间、
租约、心跳和安全错误摘要。业务记录被清理后，历史 Job 仍可依靠名称快照定位问题。

测试覆盖准备到校验串联、单表单 Job、心跳续租、过期恢复、瞬时错误重试、不可重试错误、当前
Job ID 过期保护、失败即时清理，以及多表文件的共享引用判断。
