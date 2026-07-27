# Spark Canvas 任务定义阶段开发计划（历史）

> 本文记录定义持久化阶段的原始边界，其中“不可发布/运行”的限制已由 [Canvas 真实执行设计](canvas-task-execution.md) 取代。
> 下文保留当时的阶段目标作为历史记录，不再作为当前生命周期、接口或验收依据；发布、真实执行和定时计划均以当前设计文档与实现为准。

## 1. 目标与边界

本阶段在现有任务管理中增加第二种任务类型 `SPARK_CANVAS`，完成任务创建、Canvas 定义编辑、持久化和回显闭环。

本阶段继续保留现有 Task Engine 零行 Spark 逻辑计划编译能力，用于设计期校验，但不接入正式任务执行：

- `LOCAL_SQL` 继续支持发布、立即运行和定时计划。
- `SPARK_CANVAS` 只支持创建、修改基本信息、编辑和保存定义、设计期编译校验、删除。
- 该历史阶段中的 `SPARK_CANVAS` 当时不开放发布、启停、立即运行以及计划创建和启用。
- 不新增 Spark 正式运行记录、运行快照、日志、重试、补数或调度执行逻辑。

## 2. 稳定任务模型

`task` 继续作为所有任务类型的公共表，保存名称、目录、类型、状态和审计字段；任务统一使用 UUID 标识，不维护额外编码。`TaskType` 扩展为：

```text
LOCAL_SQL
SPARK_CANVAS
```

创建任务时必须明确选择类型。类型创建后不可修改；修改类型需要删除并重新创建任务。

两种任务使用独立定义表：

```text
task
├─ task_local_sql_definition
├─ task_local_sql_input
└─ task_canvas_definition
```

`task_canvas_definition` 保存：

- `task_id`：同一任务唯一。
- `schema_version`：Canvas JSON 协议大版本，当前为 `1`。
- `schema_minor_version`：Canvas JSON 协议小版本；历史定义默认为 `0`，当前写出版本为 `2`。
- `definition_json`：与 X6 解耦的稳定 Canvas JSON。
- `version`：任务定义修改版本，从 `1` 开始，仅在语义内容变化时递增。
- `created_at`、`updated_at`。

Canvas 定义使用明确的 Java 判别联合描述 `JDBC_INPUT`、`JOIN`、`JDBC_OUTPUT`，不使用任意 Map 表达节点配置。

## 3. API

公共任务 API 保持不变，创建请求增加必填 `type`：

```http
POST /api/v1/tasks
```

现有本地 SQL 定义接口保持兼容，但只接受 `LOCAL_SQL` 任务：

```http
GET  /api/v1/tasks/{id}/definition
POST /api/v1/tasks/{id}/actions/update-definition
POST /api/v1/tasks/{id}/actions/validate-definition
```

新增 Canvas 定义接口：

```http
GET  /api/v1/tasks/{id}/canvas-definition
POST /api/v1/tasks/{id}/actions/update-canvas-definition
```

Canvas 定义响应返回：

- `taskId`
- `configured`
- `version`
- `definition`
- `updatedAt`

未配置任务返回 `configured=false`、`version=0` 和空 Canvas 定义。保存返回当前持久化版本。

任务类型与定义接口不匹配时返回 RFC 9457 `409`。Canvas 发布、运行和计划创建或启用同样明确返回 `409`，不得静默忽略。

## 4. Canvas 定义保存校验

保存允许业务上未配置完整的草稿，但必须满足可安全加载的协议结构：

- 当前读取接口兼容 `1.0/1.1/1.2`；历史 JSON 缺少 `schemaMinorVersion` 时按 `1.0`，新保存统一写为 `1.2`。
- `nodes`、`edges` 必须存在。
- 节点类型必须是三个已知类型。
- 节点和边 ID 必须是 UUID 且各自唯一。
- 边端点必须引用已有节点。
- 节点名称和布局必须满足协议范围。
- 配置列表不能为 `null`，非空数据源 ID 必须是 UUID。
- 序列化后的定义不得超过 5 MiB。

业务配置不完整、节点度数不满足、环路、字段冲突或元数据不可用仍可作为草稿保存，由现有设计期编译结果在界面中展示问题。

## 5. 后端职责

- `DataTaskService` 继续管理公共任务信息和 LOCAL_SQL 既有生命周期，同时根据任务类型组装统一摘要。
- `CanvasTaskDefinitionService` 单独负责 Canvas 定义读取、结构校验、规范化序列化和版本维护。
- `TaskRunService` 对 `SPARK_CANVAS` 明确拒绝正式运行。
- `TaskScheduleService` 对 `SPARK_CANVAS` 明确拒绝计划创建和启用。
- 删除 Canvas 任务时同步删除 `task_canvas_definition`；现有运行记录保护规则不变。

## 6. 前端流程

任务创建 Drawer 增加任务类型选择：

- 本地 SQL
- Spark 编排

任务类型只在创建时显示，编辑基本信息时只读保持原类型。任务列表增加类型列和类型筛选。

统一定义路由保持：

```text
/task/{taskId}/definition
```

入口加载任务摘要后按类型分派：

- `LOCAL_SQL`：进入现有 SQL 定义页面。
- `SPARK_CANVAS`：进入正式 Canvas 定义页面。

正式 Canvas 页面：

- 从后台加载已保存定义，未配置时打开空画布。
- 复用现有 `CanvasDesigner`、真实元数据和 Task Engine 设计期编译校验。
- 提供保存定义、返回列表和未保存变更保护。
- 节点配置尚未应用时禁止直接保存，并保留现有离开确认。
- 当时明确提示“正式执行与定时调度尚未接入”。
- 不展示发布、立即运行、运行记录或定时计划。

独立 `/task/orchestration` 页面继续作为不持久化的编排试验场。

## 7. 测试与验收

后端测试覆盖：

- 两种任务创建和类型不可修改。
- LOCAL_SQL 旧流程回归。
- Canvas 未配置定义、保存、重复保存不增版本、修改后增版本和读取往返。
- 未知节点类型、重复 ID、缺失端点、非法布局和超大定义拒绝。
- 类型错误的定义接口返回 `409`。
- Canvas 发布、运行、创建或启用计划均被拒绝。
- 删除 Canvas 任务清理定义。

前端测试覆盖：

- 创建 Drawer 类型必填且编辑时不可修改。
- 列表类型展示和类型筛选。
- 定义路由按任务类型分派。
- Canvas 定义加载、修改、保存、版本刷新和未保存保护。
- Canvas 不展示运行和定时能力。

完成后运行：

```bash
./mvnw verify
cd data-scalpel-ui && pnpm check
git diff --check
```
