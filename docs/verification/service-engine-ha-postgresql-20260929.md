# Service Engine PostgreSQL 多副本修复与验证（2026-09-29）

前置问题记录：[原 Redis 双节点验证](service-engine-ha-20260929.md)。本次按用户“轻量、尽量不增加中间件”的要求实施；没有加 Redis 依赖，没有修改服务创建/编写流程、脚本事务语义或 APISIX，没有提交代码。

## 实现结果

- 使用现有 PostgreSQL 持久化修订号、跨节点配置锁和部署 generation，替代不完整的广播同步链路。
- 标准表、SQL、脚本服务和来源 IP 策略可在同组节点收敛；节点断连重连、进程重启会加载最新快照。
- 同步未完成、配置库失联/超时的节点拒绝新业务请求并退出 readiness；单个业务数据源连接故障按数据源隔离。
- 请求侧路由/策略/数据源类型读取本机缓存；未变化的数据源不重建池。管理命令输入错误保持原错误语义，不误判为节点故障。
- 当前节点诊断接口 `/internal/v1/runtime`；运行时 OpenAPI 验证 9 个输出字段均有中文说明。诊断和 `/v3/api-docs` 使用管理 Token 保护。

配置、升级与一致性边界：[Engine 高可用设计](../design/service-engine-ha.md)。传播有窗口、配置应用期间会暂时拒绝请求，不承诺所有副本同时切换。

## 双节点实测

最终一轮：北京时间 **23:30:07–23:32:22**。Java 21.0.2、Spring Boot 4.1.0、PostgreSQL 17.4；直接使用 IDEA 的实际依赖 classpath 和本项目新编译类，classpath 不包含 spring-data-redis/lettuce，未允许 Bean 覆盖。

两个独立真实 Engine JVM，18081/18082，共用随机隔离运行库 `engine_ha_probe_1790695808035`，没有登记到 Admin。业务查询读取该隔离库的三行测试数据及视图。B 的配置库经过专用 TCP 转发 15432，业务库直连5432，用于只切断配置链路。

最终 **72 项断言全部通过**，共83条观测事件；不是将所有事件都算成通过项：

| 场景 | 实测结果 |
| --- | --- |
| 无 Redis 的两节点启动、readiness、运行态 OpenAPI | 通过 |
| IP 白名单只下发 A，同版本重发 B | 两边一致，不再重复403 |
| 数据源 A 登记、B 使用 | 通过 |
| 三类服务 A 首次部署、B 修改版本 | 两节点结果及版本一致 |
| B 错过停用，旧 SQL 路径被另一脚本服务复用 | 重连后两节点200，无旧MVC映射冲突 |
| 修改某业务数据源为不可达地址 | 登记502；关联SQL调用503，其余SQL仍200，两个节点仍就绪 |
| 修复业务数据源 | 两节点关联服务恢复200 |
| B 配置库断连11秒 | B业务503、readiness503；A继续200 |
| 断连期间 A 更新三类服务，B重连 | B无需重启自动获得最新版本 |
| 强杀测试A JVM | B三类服务直接调用200；重启A加载最新版本 |
| A/B同时修改同一SQL服务 | 显式忙碌409允许有界重试；最终共享状态和两节点结果一致 |
| IP黑名单下发和恢复 | 两边403后恢复200 |
| 三类服务停用 | 两边404，无副本残留可访问 |
| 三类服务 DEPLOYING / REMOVING 恢复 | 两边分别200 / 404 |

最后一项是**在隔离库主动构造持久化中间状态**测试恢复逻辑，不冒充精确到事务窗口的进程崩溃注入。强杀场景单独验证了已部署服务的节点故障，不是 Nginx/LB 自动故障转移试验。

本地证据：`.local/engine-ha/EnginePgHaProbe.java`、`run.ps1`、`result-pg.json` 及 A/B 日志，均不提交；报告不包含管理 Token/数据库密码。早期试跑暴露的等待条件、文档认证和非法连接参数问题已纠正，正式结果以本轮 JSON 为准。

## 初步性能测试

真实 HTTP + PostgreSQL，同一工作站同时承担压测端、两台 JVM 与数据库；每组先预热100次。以下5000次计量请求均成功，不包括功能断言请求。

| 场景 | 并发 | 计量请求 | 吞吐 req/s | P95 ms | P99 ms | 失败 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| SQL 单节点 | 16 | 1000 | 1656 | 20.29 | 31.35 | 0 |
| SQL 双节点客户端轮询 | 32 | 2000 | 1714 | 57.34 | 79.28 | 0 |
| 脚本双节点客户端轮询 | 32 | 2000 | 327 | 201.44 | 278.78 | 0 |

这是秒级、极小结果集基线，**不是生产容量或线性扩容结论**，没有改造前同条件 Engine 性能数据，不计算提速比例。脚本链路明显更慢，本次未改变 Groovy 执行/编译及日志机制，不能把SQL吞吐当成脚本吞吐。未覆盖长时间稳定性、大结果集、真实复杂SQL、跨主机网络、负载均衡、PostgreSQL主从故障、网关到Engine整链路或所有支持的数据库驱动。

## 自动化回归与检查

使用根 Maven Wrapper、项目 settings、Java21，Engine全8个测试类共20项全部通过（H2/Mockito/MockMvc），覆盖查询编译、鉴权、路由、池监控、内存读取不查库、连接池复用、generation过期回调、未就绪与失联阈值、路径复用顺序。单独的真实PostgreSQL双节点验证如上，不以H2代替集群验证。

执行命令：

```bash
./mvnw -q -pl data-scalpel-service-engine -am \
  -Dtest=EngineDeploymentConcurrencyTest,EngineClusterCoordinatorTest,EngineRuntimeReconcilerTest,DataScalpelServiceEngineApplicationTests,EngineApiStudioDataSourceServiceTest,EngineDataSourceMonitoringServiceTest,StandardServiceRequestCompilerTest,SqlServiceRequestCompilerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

另一次扩大到依赖模块全部测试的运行，在 Contracts 中有9个既有Canvas测试失败：断言版本77，实际78，Engine未修改这些协议/断言。未越界修正，**全工程测试不是全绿**。

## 环境交还

所有测试JVM和专用转发端口已关闭；随机隔离测试库已删除（测试数据不可恢复、日志保留），临时含凭据配置已删除。原 IDEA Engine PID27272/8081仍UP，用户 Redis PID38128/6379仍运行，未重启或停用它们。

IDEA原进程仍加载改造前的类；用户自行重启才会使用新实现。没有修改本机持久配置、没有提交代码。投入生产前仍须按设计处理入口健康检查、共享数据库高可用、连接池容量和真实负载持续测试。
