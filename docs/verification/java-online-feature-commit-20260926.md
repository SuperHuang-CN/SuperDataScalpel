# 在线 Java 功能提交校验（2026-09-26）

## 提交范围

语义提示完整接入与本次自动重连修复、SDK 动态 API 说明、在线草稿自动保存，以及配套入口重命名、部署说明和回归测试。

按代码段隔离提交：不包含并行 Kubernetes / Dispatcher 修改、数据源筛选、任务开发方式持久化和资源配置抽屉修改。混合文件只提交本次所需部分，工作区保留其余修改。

## 提交快照验证

从 Git 暂存区导出独立源码快照进行编译与自动化测试，避免依赖未提交的其他功能；未在该快照启动第二套开发应用。Maven 使用根 Wrapper、原 settings-superhuang.xml 和 Java 21。

- 前端 TypeScript、定向 ESLint、生产 Vite 构建通过；10 个测试文件共 71 项通过，覆盖语义连接、准备就绪、重命名、SDK 说明、片段和自动保存。冻结依赖锁文件检查通过。
- Business / TaskEngine 本次功能的 24 项针对性测试通过，包含 SDK 文档生成、33 段示例编译、接口认证、在线入口编译、开发包入口和只读代码检查。
- 真实 JDT LS 1.60.0 的 5 项测试通过，覆盖独立工作区、候选解析、诊断、重连、重命名、构造器补全和异常清理。冷启动仍需建立索引；本次预热后构造器请求约 55～120 ms，不代表生产容量或全链路时延承诺。
- 当前开发环境的浏览器连续五次断线恢复验证见 [重连记录](java-language-reconnect-20260926.md)，不修改用户源码，不执行任务。

## 非本次问题与验证边界

扩大检查发现以下原有问题，未纳入本次修复或提交，不宣称全仓测试通过：

1. Admin 的 `CanvasTaskLifecycleIntegrationTests` 第 529 / 538 行引用不存在的 `business.task.canvas` 类型，导致 testCompile 失败；Admin 生产源码编译通过。
2. `TaskEngineHttpServerTest.compilesValidCanvasAndRejectsUnsafeJson` 用 `schemaVersion: 1` 字符串替换来注入非法字段，但 HEAD 中示例已是版本 4，替换未发生，导致“预期 400、实际 200”的断言失败。本次新增 SDK 文档接口测试通过，未修改该 Canvas 测试。

没有执行生产集群压力测试、Docker 任务运行或外部数据写入。测试输出含现有 CSS 解析、Mockito 动态 Agent 和前端大包警告，不将它们解释为测试通过之外的保证。
