# Super API Gateway 第一阶段详细设计

## 1. 目标、边界与交付形态

第一阶段交付一个可验证的轻量网关，不复用 DataScalpel 的后端或前端代码。`super-api-gateway/` 可以整体移动到独立仓库，并拥有自己的 Maven Wrapper、依赖版本、配置、启动脚本、UI 和发布周期。DataScalpel 后续只能通过 `/admin-api/v1` HTTP 契约集成。

工程包含两个独立构建产物：

- `super-api-gateway-server`：Spring Cloud Gateway WebFlux 可执行应用，控制面与数据面合并部署。
- `super-api-gateway-ui`：React、TypeScript、Vite、Ant Design 和 TanStack Query 管理应用。

Server 使用 `19000` 单端口：

- `/admin-api/v1/**`：管理 API。
- `/actuator/**`：健康检查、指标和运行信息。
- 其他路径：进入唯一的 Gateway 分发 Route，再由自研内存索引匹配业务 Route。

`/admin-api`、`/admin-api/**`、`/actuator`、`/actuator/**` 在 Gateway Route Predicate 层强制排除。管理 404 与代理异常分别处理，管理路径不会被包装成上游 502。

Server 包结构固定为：

```text
controlplane  管理 API、认证、JPA 和事务
dataplane     动态路由、调用鉴权、Header 清理和 Netty 转发
runtime       revision、通知、心跳和不可变快照
accesslog     有界队列和 Kafka 访问日志
configuration 线程池、CORS 和类型化配置
```

## 2. 技术基线与线程模型

Server 使用 Java 21、Spring Boot 4.1.0、Spring Cloud 2025.1.2、Gateway 5.0.2、Spring Data JPA、Hibernate 和 PostgreSQL。应用显式设置 `spring.main.web-application-type=reactive`，只引入 Gateway WebFlux Starter，不引入 Spring MVC Starter。

由于 Gateway 5.0.2 的官方兼容线是 Boot 4.0.x，当前按已确认方案关闭 Spring Cloud Compatibility Verifier。本阶段已实际验证 Boot 4.1.0 可以启动、匹配路由并通过 Reactor Netty 转发；后续升级依赖时必须重新评估该决定。

线程和容量隔离如下：

| 执行单元 | 用途 | 默认容量 |
| --- | --- | --- |
| Reactor Netty EventLoop | 请求解析、快照读取、异步转发 | 由 Reactor Netty 管理 |
| HikariCP | JPA、revision、心跳和 LISTEN 连接 | 最大 12、最小空闲 2 |
| `controlPlaneScheduler` | 管理 API 的 JPA 与完整事务 | core 4、max 16、queue 200 |
| `snapshotReloadExecutor` | 快照加载、revision 轮询和心跳 | 固定 2 线程 |
| PostgreSQL LISTEN 线程 | 阻塞等待配置通知 | 1 个平台线程 |
| access-log worker | 出队、序列化和发起 Kafka send | 1 个平台线程、queue 10000 |
| Kafka Producer | 批处理和网络发送 | 独立 Kafka Client 线程与 buffer |

所有管理 Resource 都通过 `ControlPlaneExecutor` 使用 `Mono.fromCallable(...)` 或 `Mono.fromRunnable(...)`，并 `subscribeOn(controlPlaneScheduler)`。`@Transactional` Service 从进入到退出都运行在该线程，Repository 不会在 Netty EventLoop 上执行。数据面 Filter 不引用 Repository。

## 3. 数据模型与 schema 边界

启动时先执行：

```sql
CREATE SCHEMA IF NOT EXISTS super_api_gateway;
```

Hibernate 使用 `default_schema=super_api_gateway` 和 `ddl-auto=update`。所有 Entity 还显式声明该 schema，工程不创建跨 schema 外键，也不查询 DataScalpel 表。

核心数据：

| 表 | 职责 |
| --- | --- |
| `sag_service` | 上游 URI、访问模式、连接/响应超时、启停和外部引用 |
| `sag_route` | Service 标量 UUID、模板、order、前缀删除段数和启停 |
| `sag_route_method` | Route 支持的 HTTP 方法集合 |
| `sag_consumer` | 调用方身份与启停状态 |
| `sag_api_key` | Key 摘要、展示前缀/末四位、状态和轮换时间 |
| `sag_subscription` | Consumer 对 Service 的订阅及 ACTIVE/REVOKED 状态 |
| `sag_config_state` | 单行全局目标 revision |
| `sag_gateway_instance` | 节点心跳、版本、状态、已加载 revision 和最近错误 |

Service、Route、Consumer、API Key 和 Subscription 都保留不可修改的 `source + externalId` 外部引用。`externalId` 为空时允许手工创建多个资源；非空时数据库唯一约束为后续 DataScalpel 幂等发布提供基础。

Service 删除要求先停用且不存在 ACTIVE Subscription；删除时同步清理 Route 和已撤回 Subscription。Consumer 删除要求先停用，并同步清理 Key 和 Subscription。

## 4. 配置变更传播

每次影响数据面的管理事务都执行以下步骤：

1. 对 `sag_config_state` 单行加锁并递增 revision。
2. 在同一事务中调用 `pg_notify('super_api_gateway_config_changed', revision)`。
3. 发布本进程的 `ConfigurationChangedEvent`。
4. 事务提交后，本节点请求异步 reload；PostgreSQL 在提交后把 NOTIFY 发送给其他实例。

每个实例还每 5 秒读取目标 revision。即使 NOTIFY 丢失或监听连接短暂重连，轮询也会发现差异。

Reload 使用 `AtomicBoolean` 合并并发请求。在独立线程中一次性读取所有启用配置、校验并预编译路径，构建完整的 `GatewayRuntimeSnapshot`。只有构建成功才通过 `AtomicReference` 原子替换：

```text
load all data -> validate/compile -> immutable snapshot -> atomic install
```

失败时旧快照继续提供代理服务，节点状态变为 `DEGRADED` 并记录最近错误。数据库暂时不可用时：

- 已加载快照的数据面继续工作；
- 管理写入、revision 检查、心跳和新配置加载暂时失败；
- 不会为了数据库恢复而阻塞代理请求。

运行摘要读取节点记录时，如果 `lastSeenAt` 超过默认 30 秒未更新，则对外状态计算为 `OFFLINE`，不会把已经退出的历史实例继续显示为健康。生产部署应通过 `SUPER_API_GATEWAY_INSTANCE_ID` 为每个节点配置稳定 ID。

## 5. 路由索引与匹配

Spring Cloud Gateway 只注册一条静态分发 Route。业务 Route 不注册为数万条 Gateway Route，而是在快照中建立：

```text
(HTTP method, first fixed path segment) -> sorted candidate list
```

收到请求后先按方法和首段定位候选，再使用预编译 `PathPattern` 做完整匹配。候选排序规则为：

1. `order` 升序；
2. `PathPattern.SPECIFICITY_COMPARATOR` 的具体程度；
3. Route code 字典序。

模板支持 `{variable}`、段内 `*` 和末尾 `/**`。首段必须是固定文本；禁止查询串、fragment、空段、内联正则和动态首段。同一个 `pathPattern` 全局唯一。

匹配成功后把内存快照中的动态 `Route` 放入 `GATEWAY_ROUTE_ATTR`。该 Route 包含上游 URI、连接超时毫秒数和响应超时毫秒数，后续仍由 Gateway 的 Netty Routing Filter 完成非阻塞转发。

单次请求只捕获一次快照，路由、Key、Consumer 与 Subscription 判断不会跨 revision 混用。

## 6. 调用鉴权与代理行为

固定链路为：

```text
Request ID
-> Route index
-> API Key hash lookup
-> Consumer status
-> Subscription
-> credential removal / trusted header injection
-> Reactor Netty forwarding
-> asynchronous access log
```

行为矩阵：

| 场景 | 结果 |
| --- | --- |
| 无匹配 Route | 404 `GATEWAY_ROUTE_NOT_FOUND` |
| `PUBLIC` | 无 Key 也可调用 |
| `SUBSCRIPTION_REQUIRED` 且缺少/错误/撤回 Key | 401 `GATEWAY_API_KEY_INVALID` |
| Key 可识别但 Consumer 已停用 | 403 `GATEWAY_CONSUMER_DISABLED` |
| Consumer 未订阅或订阅已撤回 | 403 `GATEWAY_SUBSCRIPTION_REQUIRED` |
| Consumer 启用、Key 有效且订阅 ACTIVE | 转发上游 |
| 连接或其他上游网络失败 | 502 `GATEWAY_UPSTREAM_UNAVAILABLE` |
| 超过 Service 响应超时 | 504 `GATEWAY_UPSTREAM_TIMEOUT` |

API Key 默认由 32 字节 `SecureRandom` 生成，并加 `sag_` 前缀。数据库只保存 SHA-256、
前缀和末四位；明文只在创建或轮换响应中出现一次。自动化 Provider 也可以在创建或轮换
时提交 32～256 位可见 ASCII Secret；这种情况下网关只保存摘要且不在响应中回显明文。

网关在转发前删除客户端提供的：

- `X-API-Key`
- `X-Super-Gateway-Consumer-Id`
- `X-Super-Gateway-Consumer-Code`

识别到启用 Consumer 时，由网关重新注入可信 Consumer ID 和 code。公开服务收到停用 Consumer 的 Key 时仍按公开调用处理，但不会注入该身份。合法的 `X-Request-ID` 会透传，否则生成 UUID。

## 7. 管理认证、API 与错误契约

UI 使用管理员用户名/密码登录，Server 返回带 issuer 和有效期的 JWT。自动化系统使用配置化的：

```text
X-Super-Gateway-Admin-Token: <machine-token>
```

`POST /admin-api/v1/auth/login`、`GET /actuator/health` 和 `GET /actuator/info` 匿名可用，其余管理与 Actuator 地址需要 JWT 或机器 Token。比较管理员凭据和机器 Token 时使用常量时间比较。

管理 API 只使用 GET 和 POST：

| 资源 | 主要接口 |
| --- | --- |
| Auth | `POST /auth/login`、`GET /auth/me` |
| Service | 列表、详情、创建、`actions/update/enable/disable/delete` |
| Route | 列表、详情、创建、`actions/update/enable/disable/delete` |
| Consumer | 列表、详情、创建、`actions/update/enable/disable/delete` |
| API Key | Consumer 下列表、详情、创建、`actions/rotate/revoke/delete` |
| Subscription | 列表、授权、`actions/revoke/delete` |
| Runtime | `GET /runtime`、`POST /runtime/actions/reload` |

创建返回 201，删除返回 204。错误统一使用 `application/problem+json`，包含 RFC 9457 标准字段以及稳定的 `code`、`timestamp`；字段校验失败额外包含 `violations`。未知管理路径也返回 Problem Detail，不会被代理异常处理器包装成 502。

## 8. 访问日志

数据面响应提交前生成安全结构化事件并尝试放入有界内存队列。404、401、403、正常上游响应、502 和 504 都记录；未匹配请求的 Service/Route/Consumer 字段为空。

事件字段：

- schema version、timestamp、request ID、instance ID；
- Service ID/code、Route ID/code、匹配模板；
- Consumer ID/code；
- HTTP method、status、durationMs。

明确不记录 API Key、Authorization、Cookie、请求体、响应体、查询参数和原始 Header。Kafka 不可用、队列已满或发送失败时允许丢日志，只增加：

- `super_api_gateway_access_log_dropped_total`
- `super_api_gateway_access_log_send_failures_total`

发布失败不回压数据面，也不改变代理响应。

## 9. 管理 UI

UI 默认开发端口 `19080`，生产构建仍为独立静态应用。当前页面：

- 登录：管理员独立登录；
- 仪表盘：Service、Route、Consumer 数量、目标 revision 与节点状态；
- 服务与路由：管理上游、访问模式、超时和一个 Service 下的多个 Route；
- Consumer：管理 Consumer、API Key 和服务订阅，新 Key 用不可恢复的一次性弹窗展示；
- 运行节点：查看心跳、版本、状态、loaded revision、最近错误并手动 reload。

第一阶段不提供访问日志查询、调用统计、限流或配额页面。

## 10. 配置与运行

提交的默认配置位于 `src/main/resources/application.yml`，本地敏感配置只放在被忽略的 `config/application-local.yml`。统一启动命令：

```bash
./start-local-dev.sh
```

脚本明确启用 `local` Profile，并通过 `spring.config.additional-location` 读取网关自己的本地配置，不读取上级 DataScalpel 配置。

主要可调容量：

- `SUPER_API_GATEWAY_DB_POOL_SIZE`
- `SUPER_API_GATEWAY_CONTROL_CORE_THREADS`
- `SUPER_API_GATEWAY_CONTROL_MAX_THREADS`
- `SUPER_API_GATEWAY_CONTROL_QUEUE_CAPACITY`
- `SUPER_API_GATEWAY_RELOAD_THREADS`
- `SUPER_API_GATEWAY_REVISION_POLL_INTERVAL`
- `SUPER_API_GATEWAY_HEARTBEAT_INTERVAL`
- `SUPER_API_GATEWAY_INSTANCE_STALE_AFTER`
- `SUPER_API_GATEWAY_ACCESS_LOG_QUEUE_CAPACITY`
- Kafka Producer 的 batch、linger、delivery timeout 等 Spring Kafka 配置

## 11. 第一阶段验证与后续边界

已验证 Boot 4.1.0 + Gateway 5.0.2 Reactive 启动和 Netty 转发；管理面使用专用线程池；公开与订阅鉴权矩阵；路径和保留地址隔离；真实 502/504；真实 PostgreSQL 下的事务后本节点 reload；两个实例通过 NOTIFY 加载相同 revision 并由第二节点成功代理。实现还包含 5 秒 revision 轮询和 Kafka 失败不影响响应的降级路径。数据结构以 1 万 Route、10 万 Key、100 万 Subscription 为索引设计基线，本阶段不设置性能压测门槛。

明确留到后续：

- 限流、周期配额与计费；
- 日志检索、聚合统计和告警平台；
- JWT/OAuth2 业务调用认证；
- 动态插件和 WebSocket；
- 控制面与数据面拆进程；
- 固定性能验收指标。

## 12. Provider 集成契约补充

Service、Route、Consumer、API Key 和 Subscription 列表支持同时提交 `source` 与
`externalId` 精确过滤；两个参数必须同时出现，结果最多一个。该能力用于外部控制系统在
“远端提交成功、本地 Binding 尚未完成”后幂等恢复。

API Key 详情返回不可逆 `secretDigest`，不返回明文。Subscription 的物理删除 Action
只接受 `REVOKED` 资源；重新授权同一 Consumer + Service 时，现有资源的外部引用必须与
请求一致，禁止把其他来源的订阅重新绑定给调用方。

这些接口保持来源中立，不包含 DataScalpel Java 类型或数据库访问。调用系统使用公开
HTTP DTO 和 Machine Token 自行实现适配。
