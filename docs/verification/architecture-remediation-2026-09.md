# 2026-09 架构整改与统一检查

日期：2026-09-16。范围：当前 DataScalpel 工作树，包含原有未提交的本体、模型引用保护和前端改动。三批实现完成，检查结果包含下述基线失败与验证限制，不表示全仓测试通过。实施顺序见[整改计划](../design/architecture-remediation-2026-09.md)。

## 架构结论

继续保持模块化单体。Contracts、Dialect、Business、Dispatcher 与执行引擎的职责方向合理；本次问题主要在异步操作的生命周期完整性、重复扫描和大类内部职责，不需要新增微服务、框架、模块或权限体系。

| 原问题 | 已落地的整改 |
| --- | --- |
| 实时 START 重试时 STOP 可先行，迟到 START 可能重新启动外部作业 | Outbox 将批任务 SUBMIT 和实时 START 都作为前置屏障；Dispatcher 持久化 stop-before-start 身份记录，迟到 START 不创建执行。Admin 不再将 Dispatcher 404 当作停止成功。 |
| 数据库写入超时终态后，取消失败或提交结果不确定的外部作业可能失去监管 | 执行结果与外部终止、日志归档、清理分离。终态仍可恢复外部句柄并重试终止，迟到提交结果不改变终态；确认外部终止后可释放运行配额。 |
| 同一服务的部署/移除及恢复可能交错，旧操作影响新路由和状态 | Service Engine 用固定数量的锁覆盖一次完整运行时操作；恢复在锁内重新读取状态。管理端以 revision 和每次操作的 operationId 校验回写，外部调用保持在事务外。 |
| 空文件表 ID 的草稿影响删除校验，删除时扫描解析所有 Canvas | 定义保存与文件引用投影同事务更新；空 ID 不生成引用，删除查询使用索引。启动按页修复存量定义，无法解析的定义记录索引失败并继续保守保护。 |
| 周期任务扫描历史或相互阻塞 | Dispatcher 观察和清理各处理至多 50 条到期记录，完成清理的历史被查询排除；重试间隔从 5 秒增长到最多 5 分钟。Outbox、协调、准入、观察及清理分别归属有界调度线程。Admin 协调按 UUID 游标每轮最多 50 条。 |
| 本体列表逐行重复解析关系和查询模型元数据 | 同一次列表读取共享定义、关系及访问编码索引，模型与字段按实际相关 ID 批量加载。仍保留一次全类型库读取，未宣称全库大小不影响请求成本。 |
| Canvas 中央解析、默认值和摘要承担所有节点知识 | 64 个节点分别持有 parser/defaults/summary，spec 显式接入；公共值解析按主题拆分，空间协议版本检查与单位选项分离，兼容入口保留。 |
| TaskRunService、DataModelService 混合查询、执行和外部 I/O 职责 | 提取 TaskRunArtifactQueryService、DataModelDataQueryService、DataModelPhysicalChangeService；模型定义校验集中到 ModelDefinitionService。原 Service 保留入口及生命周期编排，任务提交公共状态约束收敛。 |

没有修改公开 REST 路由、JSON 协议版本或引入生产依赖。用户已有的指标/本体模型删除保护保留。实体新增内部状态和引用投影，沿用当前 `ddl-auto=update`。

## 已执行的检查

### 后端构建与回归

根目录执行，使用原 Wrapper 与 `.mvn/maven.config`：

```sh
./mvnw -pl data-scalpel-business,data-scalpel-task-dispatcher,data-scalpel-service-engine -am package -Dmaven.test.skip=true

./mvnw -pl data-scalpel-business,data-scalpel-service-engine,data-scalpel-task-dispatcher -am test \
  -Dtest=CanvasFileReferenceIndexTest,DispatcherExecutionReconciliationServiceTest,TaskRunServiceTrialPreviewResultTest,EngineDeploymentConcurrencyTest,DispatcherTerminationRecoveryTest,DispatcherExecutionFlowIntegrationTest,DispatcherTaskExecutionTest \
  -Dsurefire.failIfNoSpecifiedTests=false

./mvnw -pl data-scalpel-business,data-scalpel-task-dispatcher -am test \
  -Dtest=BusinessObjectReadBatchTest,DispatcherExecutionFlowIntegrationTest,DispatcherTerminationRecoveryTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

生产构建通过。首轮 17 项回归通过，补充轮 10 项通过；两轮按最终用例去重为 20 项。覆盖：持久化 STOP 后迟到 START、超时后的取消重试、不确定提交恢复、迟到句柄、并发部署与恢复、空 ID 引用、损坏索引保护、404 协调语义、试运行产物读取、本体批量读取、清理查询过滤/分页及退避上限。

另使用当前编译的 Business 实体、实际 Repository 注解中的 HQL 和已有 H2 依赖进行了独立数据库检查：START 退避时 STOP 不可领取、START 到期后仅领取 START、START 发布中 STOP 仍被阻挡、START 发布完成后 STOP 放行，均通过。检查使用进程内临时数据库、事务回滚，没有连接共享开发数据库或另起应用。相同场景保留在 `TaskExecutionMessagingIntegrationTests.stopWaitsForStreamingStartToFinishRetrying`。

日志：`/tmp/datascalpel-remediation-build.log`、`/tmp/datascalpel-remediation-targeted-tests.log`、`/tmp/datascalpel-remediation-final-tests.log`、`/tmp/datascalpel-remediation-outbox-check.log`。临时数据库检查源文件：`/tmp/DataScalpelOutboxOrderCheck.java`。

### 前端

在 `data-scalpel-ui` 执行：

| 检查 | 结果 |
| --- | --- |
| `pnpm exec tsc -b --pretty false` | 通过；后续 `pnpm build` 再次包含类型检查。 |
| `pnpm build` | 通过。仍有现有大 chunk 与 TaskOrchestrationPage 同时静态/动态导入警告。 |
| `pnpm exec eslint src/modules/task/canvas` | 通过。 |
| `pnpm test -- src/modules/task/canvas` | 305 通过、32 失败，失败来自 5 个测试文件。与 HEAD 基线对应测试对照，失败用例名称完全一致，无新增失败。 |
| 空间兼容性拆分后的 2 个测试文件 | 6 项通过。 |
| Canvas 内部静态运行时依赖扫描 | 526 个生产 TS/TSX 文件，无多文件强连通环；排除类型导入与测试文件，范围不涵盖整个前端或动态运行时行为。 |
| `pnpm lint` | 剩余 4 个错误，均在未修改的 DataServiceSpatialPreviewPanel.tsx：3 处 set-state-in-effect，1 处 preserve-manual-memoization。 |

32 个既有失败涉及 `canvasDefinitionIO.test.ts`、`useCanvasMetadataSnapshot.test.tsx`、`CanvasDefinitionModal.test.tsx`、`CanvasNodeInspector.test.tsx`、`CanvasNodePalette.test.tsx`。主要是旧字段期望、资源 Mock、分类数量和 UI 文案未同步。基线仅导出到临时目录运行单元测试，没有启动另一套应用。

日志：`/tmp/datascalpel-remediation-ui-build.log`、`/tmp/datascalpel-remediation-canvas-tests.log`、`/tmp/datascalpel-remediation-spatial-tests.log`、`/tmp/datascalpel-remediation-lint-final.log`。基线结果：`/tmp/datascalpel-ui-baseline-results.json`。依赖检查：`/tmp/datascalpel-remediation-canvas-cycles.json`。

### 开发环境

确认现有端口无占用后，从根目录运行 `./start-local-dev.sh --threads 2`，沿用用户 `local` 配置。启动成功并保留运行会话：

| 端点 | 结果 |
| --- | --- |
| Admin `18080/actuator/health` | UP |
| Service Engine `8081/actuator/health` | UP |
| Task Engine `18091/health/ready` | UP |
| Dispatcher `18092/health/ready` | UP，数据库、Kafka、制品、监听器、Backend 均 UP |
| 前端 `18887` | HTTP 200 |

真实启动验证了 Spring 装配、JPA 映射及新 Repository 查询初始化。没有在共享环境执行物理表破坏性变更，没有将健康检查视为完整端到端业务验收。日志：`/tmp/datascalpel-remediation-local-dev.log`。

## 验证限制与后续方向

1. **优先修复测试基线。** Admin 的 `testCompile` 被已有测试旧包名、构造参数和 API 引用阻断。尝试运行 Outbox 与模型集成回归未进入测试执行，不能宣称这些集成回归通过；仅局部核对旧包名后又暴露 24 处旧签名/符号错误，未扩大修改其他测试。模型查询/物理变更提取完成编译、代码审查及应用装配，外部 DDL 行为未通过该集成套件验证。相关日志：`/tmp/datascalpel-remediation-outbox-tests.log`、`/tmp/datascalpel-remediation-admin-tests.log`。前端上述 32 项测试失败和 4 项 lint 错误也应同步治理，恢复可用的回归信号。
2. **处理历史 Canvas 数据。** 启动发现下列 6 个定义大版本不受当前协议支持，引用投影无法恢复。任一未完成索引会阻止文件表删除/替换，以及存在文件表时的解析参数修改，这是当前保守保护的明确边界。应定位并按现行协议重建/重新保存这些定义，或经业务确认删除废弃任务；本次没有自动迁移或删除历史业务数据。

   ```text
   09ae7776-d590-4153-9709-ff7e27440e3d
   25aa2746-d318-4b0c-8528-348b5e4ede4b
   4d4d7e5e-98ef-4418-ae4a-38d07fd887ef
   6afb237c-1496-418f-9205-3ebee0e179df
   ab1fe2fc-4c20-48af-84c8-712ad0740b17
   ea38184a-4233-41f9-9462-492eb61ab192
   ```

3. **按实际规模继续优化。** 本体仍一次加载全类型库；规模上升时应基于现有关系投影按需读取，不先引入缓存一致性机制。Service Engine 的串行锁只覆盖单运行时，当前没有多副本共享同一引擎身份的协调保证。
4. **继续沿职责拆分。** TaskRunService 和 DataModelService 仍保留较大业务编排体量，后续结合具体需求拆分稳定职责，避免仅按行数新增接口或模块。前端继续修正路由入口的静态导出，减少大型能力被提前加载。

最终 `git diff --check` 通过。改动保留在工作树，未提交或发布。
