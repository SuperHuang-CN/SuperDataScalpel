# 首页统计 V1 测试记录

日期：2026-09-30。结论：首页实现与专项验证通过；全量工程检查未全部通过，不能据此宣称整仓测试全绿。

## 1. 本次功能验证

| 检查 | 结果 | 覆盖内容 |
| --- | --- | --- |
| 后端生产编译 | 通过 | 使用 Java 21、根 Maven Wrapper 和既有 settings-superhuang.xml |
| 后端首页集成测试 | 10 项通过，0 失败、0 错误 | 模型发布/草稿/停用、空分层与失效引用；七种任务类型；服务启用与部署状态；网关绑定去重、排除已删除服务；正式质量结果去重及同时间去重；实时部署活动/最新世代与试运行排除；试运行/模拟运行排除；技术失败独立提示；分页与参数校验；权限与 ProblemDetail；网关完整小时及跨服务/跨小时去重；资产独立状态 |
| 前端专项 Vitest | 3 文件、9 项通过 | 图表状态归类、空时间桶、运行下钻参数、无权限/隐藏区域不发请求、单区块失败隔离、模型“已发布且未分层”筛选 |
| 真实页面 Playwright | 2 项通过 | 七个区域、质量明细、近7天切换、未分层下钻、1920×1080 与900×1000无横向溢出；单接口503后其他区块可用、重试恢复 |
| 开发环境接口 | 8 个接口全部200 | 模型/任务/服务/资产摘要、质量统计/明细、网关使用摘要、运行概览 |
| 开发环境口径对照 | 14 组通过 | 6 个实际分层与列表数量一致、4 种服务类型与启用列表一致、4 类质量明细与概览一致 |
| 运行时 OpenAPI | 11 个响应结构通过 | 检查实际 /v3/api-docs，包含嵌套分层/类型结构，字段中文说明齐全 |
| 本次变更文件 ESLint | 通过 | 未对原有无关文件关闭规则 |
| 前端模块边界 | 通过 | 1013 个生产 TypeScript 文件；跨域使用公开入口 |
| 前端生产构建 | 通过 | pnpm build；现有大包体积警告仍存在 |
| 界面核对 | 通过 | 上下半屏及窄屏截图检查；Impeccable detector 无发现 |

后端专项测试使用 application-test.yml 的隔离 H2 数据库；测试中替换需要 PostgreSQL 表锁的权限初始化 Runner，并提供受控的 OperationsAccess 与制品存储测试替身。每个测试都检查首页不调用制品存储。Resource 权限仍由真实 Spring Security 方法授权执行；真实 PostgreSQL 接口查询另在开发环境验证。

## 2. 全量检查结果与阻塞

已实际执行根目录 `./mvnw.cmd verify` 和前端 `pnpm check`，并继续单独执行前端全量 `pnpm test` 与生产构建。为让后端尽可能继续检查其他模块，又执行了 `verify -Dmaven.test.failure.ignore=true` 收集失败；此参数仅用于收集，不将失败解释为通过。

- **Contracts**：91 项测试中9项失败，空间节点协议测试仍断言节点数77，实际为78。
- **Business**：320项中8项断言失败、5项错误，涉及已有 Canvas 版本验证、SQL输入声明、Kong路由匹配等。
- **Admin 全量测试编译**：已有 ComputeEngine、Canvas、Gateway、DataModel 等测试沿用旧构造方法或旧接口，编译失败，后续 Reactor 模块未在此次根 verify 中完成。
- **既有运行工作台集成测试**：尝试单独执行后，测试初始化因 H2 不支持 `lock table sys_permission in share row exclusive mode` 失败。首页专项测试隔离此启动依赖，统计查询仍由真实 JPA/JDBC 执行。
- **全量 ESLint**：现有 `ComputeEngineDiscoveryDrawer.tsx:75` 触发 `react-hooks/set-state-in-effect`。本次修改文件的 ESLint 已单独通过。
- **前端全量 Vitest**：已完成，173 个测试文件中150个通过、23个失败；751项测试中718项通过、33项失败，耗时2471.15秒。失败涉及原有 Canvas/X6模块加载、组件文案断言、20秒超时等，详细输出见 `.codex-homepage-tests.log`。未逐项完成失败归因，不将所有超时认定为既有缺陷。此全量运行开始于新增专项测试完成前；首页最终3文件、9项专项结果以上表的单独运行记录为准。

这些失败没有通过删除测试、关闭规则、修改生产契约或更换数据库规避。Admin专项验证时临时限定 testIncludes，只编译首页测试类；执行后已恢复原始pom.xml。新增测试放在标准测试目录，修复既有测试后可随完整工程正常运行。

## 3. 开发环境与证据

- 复用前端 http://localhost:8887；由根目录 `start-local-dev.sh --admin-only --threads 2` 启动后端 http://localhost:8080，保留运行环境。
- 未更换 Profile、数据库、Schema、端口或外部服务地址。测试只读现有业务数据，没有向开发库插入统计样本。
- Playwright配置移除自动独立启动Vite，要求先按工程约定启动开发环境；支持 `DATASCALPEL_TEST_BASE_URL`、`DATASCALPEL_TEST_USERNAME`、`DATASCALPEL_TEST_PASSWORD` 指定现有环境。
- 运行证据保存在本地 `.local/homepage-api-verification.json`、`.local/homepage-drilldown-verification.json`、`.local/homepage-openapi-verification.json` 与 `.codex-homepage-*.log`。证据日志不作为生产源码提交。
- [实际界面上半屏](assets/homepage-implemented-desktop.png)、[实际界面下半屏](assets/homepage-implemented-lower.png)使用开发环境真实数据。

## 4. 实际范围

质量明细展示每模型最近一次正式有效结果及其时间，模型链接进入基本详情；原有单模型质量页的默认查询行为未改变。网关指标仅覆盖现有小时表中已识别服务、身份未冲突的日志，匿名调用可进入调用次数，但不能虚构活跃调用方。历史成功、发布或启用状态均不被表述为当前健康证明。

