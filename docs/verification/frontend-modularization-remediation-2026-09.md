# 2026-09 前端模块化整改实施与验收

日期：2026-09-16。范围：公开入口与路由加载边界、Canvas 遗留 Inspector、`DataModelPage` 职责拆分，以及计划中明确的 Canvas 测试和空间服务预览 lint 基线。本次保留单一 React 应用，没有新增生产依赖、全局状态管理或微前端，也没有修改 Canvas JSON 协议或业务 API。

## 实施结果

### 公开入口与加载边界

- 业务模块 `index.ts` 不再导出页面，只保留实际跨模块使用的 API、Hook、类型、工具和业务组件；模块内部继续使用相对引用。
- 登录页保持同步加载，其余路由页面统一从所属页面文件通过 `React.lazy` 加载，保留原权限保护和 `Suspense` 提示。
- 应用路由和外壳直接读取任务视图配置及所需 Hook，避免为少量轻量能力加载整个任务模块公开入口。任务血缘对模型类型的引用改经模型公开入口。
- 新增 `scripts/check-module-boundaries.mjs` 并接入 `pnpm check`，检查 `shared` 反向依赖、业务模块间深入引用、公共入口导出页面和 Canvas 静态运行时循环。

浏览器在新的首页标签页记录到 65 项资源，其中没有 `TaskOrchestrationPage`、`modules/task/canvas`、AntV X6、`MonacoSqlEditor` 或 Monaco；进入 `/task/orchestration` 后，`TaskOrchestrationPage`、`CanvasDesigner`、X6 和节点注册资源按页加载。该检查使用根脚本已经启动的用户开发环境，前端端口为 `18887`。

### Canvas Inspector 归位

- Kafka 输入、Join、StreamJoin、Rename、JDBC 输出、Kafka 输出和模型输出 Inspector 已迁回各自节点目录；输出节点的内部设置表单也保留在节点目录。
- 字段预览提取为 Canvas 专用 `CanvasFieldPreview`，校验继续使用既有组件；共享解析、默认值和摘要只保留真实复用能力。
- 文件输入、HTTP 输入和模型输入继续使用现行节点实现。`CanvasLegacyInspectors.tsx` 及其引用已删除。
- 保留 Inspector Adapter、配置 JSON、批流模式、失效值、应用校验、错误展示及脏状态处理方式。

### DataModelPage 与空间服务预览

- `useDataModelListState` 负责草稿/已应用筛选、目录、分页、URL 状态和跨页选择；仍复用既有路由解析与 TanStack Query。
- `useDataModelListActions` 负责发布、停用、删除、导出、批量发布和统计刷新；仍复用既有请求 Hook。统计刷新维持最多 20 项、并发 3，批量发布维持最多 50 项，并保留行级 loading、部分失败反馈和删除确认。
- 筛选栏、结果工具栏和表格拆为页面专用组件；页面继续负责权限、布局及 Drawer/Modal 组合，没有增加通用列表框架。
- 空间服务预览明确区分服务端样式和本地草稿，通过输入版本判断预览与图例是否过期；旧请求会取消，迟到响应会丢弃，对象 URL 在替换和卸载时释放。

## 自动检查

在 `data-scalpel-ui` 执行：

| 检查 | 结果 |
| --- | --- |
| `pnpm exec tsc -b --pretty false` | 通过。 |
| `pnpm lint` | 通过，空间服务预览原 4 项错误已清除。 |
| `pnpm check:boundaries` | 通过，覆盖 959 个生产 TypeScript 文件；公开入口、依赖方向、跨模块深入引用和 Canvas 静态运行时循环均未发现违规。 |
| `pnpm build` | 通过；不再出现 `TaskOrchestrationPage` 同时静态/动态导入警告，Canvas、X6、Monaco 保持独立加载边界。仍有既有的大 chunk 体积提示。 |
| `pnpm test -- src/modules/task/canvas` | 67 个测试文件、341 项测试全部通过。原 5 个文件中的 32 项失败已按现行契约修复，没有跳过用例或删除原行为断言。 |
| `pnpm test` | 首次完整运行 139 个测试文件通过、13 个测试文件失败，568/581 项测试通过。稳定失败和并发运行波动均记录在下节，Canvas 独立全量保持通过。 |

## 全前端测试的范围外失败

首次完整运行稳定复现以下 13 个失败文件：

- `ApiConsumerAccessDrawer.test.tsx` 3 项、`ServiceEngineDrawer.test.tsx` 3 项。
- `JdbcConnectionOptionsFields.test.tsx`、`FileDatasetParseQueueDrawer.test.tsx`、`FileDatasetTableResultPanel.test.tsx`、`DataModelTasksPanel.test.tsx`、`LocalSqlTaskDefinitionPanel.test.tsx`、`SqlServiceEditor.test.tsx`、`SqlTestPanel.test.tsx` 各 1 项。
- `CanvasTaskDefinitionPanel.test.tsx`、`TaskModelsPanel.test.tsx`、`TaskRunDetailDrawer.test.tsx`、`TaskDetailPage.test.tsx` 在收集阶段因 X6 的 CommonJS/ESM 入口不兼容失败，未进入测试执行；单独复跑这 4 个文件结果相同。

为保存机器可读清单又执行了一次 JSON reporter。并发运行得到 136 个文件通过、16 个文件失败，564/581 项通过；除上述稳定失败外，`ComputeEngineDrawer` 2 项和两个 Canvas 交互用例出现仅含 `STACK_TRACE_ERROR` 的瞬时失败。随后单独复跑这 3 个文件，9 项测试全部通过，因此不把它们计入稳定失败，也不改变 Canvas 独立全量 67 文件、341 项全部通过的结论。JSON 结果位于 `/private/tmp/datascalpel-frontend-vitest.json`。

这些稳定失败属于数据服务、文件数据集、数据源、服务引擎、模型关联和任务详情等本轮未纳入整改的功能。没有通过跳过测试、关闭规则或削弱断言处理它们。

## 页面验证

页面验证复用根目录 `./start-local-dev.sh` 已启动的用户开发环境，没有另起 Vite、后端、数据库或 Mock 服务。

- 首页正常登录和渲染，新的首页标签页未加载 Canvas、X6 或 Monaco；进入任务编排页后相关资源正常加载。
- 加载内置拓扑示例后，JDBC 输入 Inspector 正常打开；修改数据源后显示“未应用”，切换节点弹出“节点配置尚未应用”，放弃草稿后选择切换到目标节点。
- 内置示例自动预检返回明确的 7 项配置错误。另打开已停用的验证任务 `HelloStreaming2` 进入定义编辑页，Task Engine 显示“引擎校验通过”和“血缘预览：字段完整覆盖”，保存按钮保持禁用，未修改或保存该任务。
- 验收只覆盖 Inspector、节点切换、未保存修改和 Task Engine 预检结果。没有点击“试运行”“立即运行”“启用”或任何会处理真实数据的入口。

最终改动保留在工作树，未提交、未发布。
