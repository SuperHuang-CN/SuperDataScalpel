# TaskWorkflow V1

状态：已实现。适用于单 Admin/Business 执行实例；沿用根开发约定的验证政策。

## 任务与定义

`WORKFLOW` 是 `DataTask` 的一种类型，复用任务目录、权限、发布/停用、Cron 计划和运行历史。工作流不绑定计算引擎，不生成 Runner Manifest。Business 推进依赖；子任务使用自身的 Local SQL 或 Spark 执行方式。

支持 Local SQL、Spark Canvas 批任务、Spark 模型质检和 Spark JAR 批任务。V1 不支持嵌套工作流、实时子任务、执行重试、续跑、条件分支、参数传递和数据补偿。每次重新运行都从头开始。

定义存储在 `task_workflow_definition`，每个任务一条定义；内容变化时递增 `version`。定义 JSON 与 X6、Spark Canvas 协议独立：

```json
{
  "schemaVersion": 1,
  "maxParallelism": 4,
  "nodes": [{"id": "extract", "taskId": "引用任务 UUID"}, {"id": "load", "taskId": "引用任务 UUID"}],
  "edges": [{"source": "extract", "target": "load"}],
  "layout": {"extract": {"x": 40, "y": 40}, "load": {"x": 320, "y": 40}}
}
```

节点 ID 唯一且不超过 100 个字符。草稿可暂缺任务引用和依赖配置；发布/启用要求至少一个节点，引用有效且已发布，连线无悬空端点、自环、重复或环路，并行度为正整数。支持多个入口、出口和独立分支。

同一任务可在不同节点被引用，每个节点创建独立子运行。工作流子任务遵守现有任务并发互斥；发生冲突即提交失败，不借用其他运行、不等待重试。

## 运行与依赖

父、子运行均保存在 `task_run`。子运行记录 `parentRunId`、`workflowNodeId`，二者组成唯一约束；不新增工作流运行表或节点运行表。

父运行在提交时保存完整定义快照，修改工作流不改变已开始运行的 DAG。子节点实际启动时读取当时最新的已发布定义，保留该次子运行自己的不可变快照与版本。子任务停用后重新编辑发布，可能影响尚未启动的节点；子任务仍停用或引用失效时，父工作流失败。

父触发类型为 `MANUAL` 或 `SCHEDULED`；子触发类型为 `WORKFLOW`。子运行不继承父计划 ID 和触发时间。Spark JAR 的现有 SDK 触发协议不扩展，工作流来源及父子关系以管理端 TaskRun 为准。

全部前置节点的子运行 `SUCCESS` 后才提交后继。模型质检执行成功但 `qualityConclusion=FAILED` 仍视为依赖成功，已有质检告警继续独立工作。只有全部节点成功才标记父运行成功。父运行不汇总影响行数。

推进器默认每秒扫描（`data-scalpel.task.workflow.poll-interval`），使用独立 4 线程、100 队列容量的线程池；每轮仅准备/提交就绪节点，不占用线程等待子任务结束。排队、执行和停止中的子运行占用并行槽位。队列暂满时父运行保留，下一轮再推进。

提交在最终短事务中锁定父运行，重新检查状态、节点唯一性、依赖和并行度，然后创建子运行及 Spark Submit Outbox。外部 JDBC、编译和制品操作在管理事务外。父运行停止后，准备中的节点不得继续创建子运行。

## 失败、取消和重启

任一节点执行失败、超时、被单独取消或提交失败时，父运行立即失败并停止其他活动子任务。停止仅按本次父运行关联操作，不改变引用任务的独立运行或 Cron 计划。未执行节点显示阻断，准备失败节点通过父运行的安全错误字段定位。

取消父运行后先进入 `CANCEL_REQUESTED`：停止派发，取消排队子任务并实际停止运行中的子任务；全部已结束后进入 `CANCELLED`。外部系统报告 `EXECUTION_TERMINATION_UNCONFIRMED` 时，父运行失败并明确展示停止未确认。已结束父运行不会因迟到的子事件再次推进。

Local SQL 使用本次 JDBC 执行专属取消句柄，调用 Statement.cancel 并中止连接；Worker 退出后确认取消终态。OVERWRITE 保留事务回滚行为，已提交的数据不做补偿。排队 SQL 被取消后不得执行。

工作流停止 Spark 子运行时通过现有 Outbox 投递 `FORCE_TERMINATE_EXECUTION`，由 Dispatcher 硬终止 Backend 并返回权威结果。Outbox 保证同次执行的提交消息确认发布后才发送停止类命令，防止提交发布重试被取消消息超越。消息投递重试与任务执行重试不同，前者继续保留。

Admin 启动时先执行已有 Local SQL 中断标记，再把未结束工作流标记为 `FAILED / APPLICATION_RESTARTED`，持久化外部子运行的停止请求。随后开放工作流推进并启动 Quartz，不恢复 DAG。部署仅支持一个负责工作流的 Admin 实例。

## 接口与界面

沿用 `/api/v1/tasks` 及其 `actions/run`、`actions/publish`、`actions/disable`、`actions/enable` 等生命周期接口。运行取消沿用 `/api/v1/task-runs/{runId}/actions/cancel`，现支持 Local SQL 与工作流。

| 接口 | 权限 | 返回 |
| --- | --- | --- |
| GET `/api/v1/tasks/{id}/workflow-definition` | task.view | taskId、version、definition |
| POST `/api/v1/tasks/{id}/actions/update-workflow-definition` | task.update | 保存后的定义；请求为 `{definition: ...}` |
| POST `/api/v1/tasks/{id}/actions/validate-workflow-definition` | task.publish | valid、problems（code/message/nodeId/edgeIndex） |
| GET `/api/v1/task-runs/{runId}/workflow` | task.view | run、definition、totalNodes、succeededNodes、hasActiveChildren、nodes |

节点投影带 taskId、taskName、status、childRun 和 message；childRun 复用普通运行响应，含实际定义版本和诊断。`WAITING`、`BLOCKED` 是投影状态，不新增持久化运行状态。定时重叠跳过的父运行不创建子运行。

前端工作流编辑器按需加载 X6，支持任务搜索、连线、并行度、保存、校验、发布和未保存离开保护。运行图和表格可跳转子运行详情，子运行可返回父流程。父运行失败后仍有活动子任务时持续轮询并显示停止进度。

工作流接入现有运行工作台和终态告警，不新增告警系统。数据血缘、执行日志和制品在子任务中查看。

## 升级与验收

已有 Admin 数据库在停机期间运行 [增量 SQL](../operations/task-workflow-admin-postgresql.sql)，随后部署后端与前端。新库由现有 Hibernate `ddl-auto=update` 创建表。无需 Runner、SDK 或 Manifest 协议升级。

验收关注串行/并行汇聚、并发上限、质检结论独立、启动时子定义版本、父快照固定、无重复子运行、提交与取消竞态、JDBC 实际取消、Spark 提交/停止顺序、失败清理、重启失败及独立运行不受影响。验证环境与强制程度以根开发约定和具体任务要求为准。
