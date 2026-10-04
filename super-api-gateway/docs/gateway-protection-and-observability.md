# 网关保护与可观测性

状态：2026-09-29 实现。独立网关能力，不依赖外部控制系统源码或数据库。

## 保护策略

`GET /admin-api/v1/services/{id}/traffic-policy` 查询；
`POST /admin-api/v1/services/{id}/actions/update-traffic-policy` 全量更新。
请求字段：

| 字段 | 语义 |
| --- | --- |
| requestsPerSecond | 单节点、单服务每秒令牌数，0～1000000；桶容量等于一秒额度，0 不限制 |
| consumerRequestsPerSecond | 单节点、单服务、单 Consumer 限流，同上；匿名请求不扣 Consumer 额度 |
| maxConcurrentRequests | 单节点、单服务并发数，0～100000，0 不限制；直到响应完成、失败或取消才释放 |
| maxRequestBytes | 请求体字节上限，0～1073741824，0 不限制；检查 Content-Length 和实际流式读取字节 |
| allowedCidrs / deniedCidrs | 各最多 64 条非空 IPv4、IPv6 或 CIDR；空白名单不限制，黑名单优先；只取 TCP 对端，不信任 X-Forwarded-For |

响应包含 `policy`、`targetRevision`（数据库目标修订）、`loadedRevision`（当前响应节点加载修订）、`scope=NODE`。
默认值均为零/空列表，旧数据自动等价于不限制；不改变原来 PUBLIC / SUBSCRIPTION_REQUIRED 语义。
托管 `source=DATASCALPEL` 服务必须通过机器认证修改，普通网关 UI 显示只读；独立手工服务仍可编辑。

拒绝顺序为认证/订阅 → IP → 已知请求体长度 → 服务/Consumer 额度与并发 → 代理流式大小检查。
IP 拒绝 403；超大请求 413；令牌不足或并发满 429 并带 `Retry-After: 1`。
服务令牌已扣而 Consumer 被拒绝时不退还服务令牌，限制的是进入该服务的尝试压力。
限流状态不因快照更新而清零；内存最多保留 100000 个服务/Consumer 桶，超出时对新桶 fail-closed 返回 429。
状态随节点重启清空，没有跨节点全局额度、日/月配额或账务保证。

流式超限不是整包原子校验：超限前的字节可能已到达上游，不应依靠它保证上游无副作用。
节点位于负载均衡器后时，IP 策略看到的是负载均衡器地址；本版未引入可信代理头解析。

## Key 与订阅有效期

`GET /admin-api/v1/access-validities/{kind}/{id}`；
`POST /admin-api/v1/access-validities/{kind}/{id}/actions/update`。
`kind` 为 `keys` 或 `subscriptions`；`id` 是网关对象 UUID。

- `validFrom` / `expiresAt`：可空 ISO-8601 UTC 时间，起点包含、终点不包含；同时存在时起点必须早于终点。空起点立即生效，空终点不自动到期。
- `requestsPerSecond`：0～1000000，只用于订阅；Key 必须为 0。订阅和服务的 Consumer 限流同时设置时取非零较小值。
- 响应还有 `state`（ACTIVE / REVOKED / NOT_YET_VALID / EXPIRED）和目标/本节点加载修订。

每个请求按当前时钟校验，不依赖定时停用或重新同步。无效 Key 返回 401，失效订阅返回 403。
修改有效期不会恢复 REVOKED 对象、不改变 Key 明文；轮换 Key 和重新同步现存对象保留有效期。
删除对象后重建是新配置，原有效期不保留；所有节点需同步系统时间。

## 代理资源边界

默认每上游连接池最多 500 个连接、1000 个等待者、等待最长 1000 ms。连接空闲 30 秒、存活 5 分钟、每 30 秒清理。
SCG 固定池原本等待队列无界；本实现只替换连接提供者，保留其 TLS、连接/响应超时及其他 HTTP 配置。
可配置 `spring.cloud.gateway.server.webflux.httpclient.pool` 数值，等待容量由 `super-api-gateway.max-pending-connections` 设置。
这是**每上游池**限制，不是进程全局连接数。大量不同上游仍须评估总连接量。
上游连接池满/等待超时返回 503，连接失败 502，上游响应超时 504。所有错误使用原有 ProblemDetail 协议。
没有添加自动重试业务请求，避免 POST 被重复执行。

## 访问日志与监控

Kafka 默认 topic `datascalpel.gateway.access.v1`，事件协议 `schema_version=1.0`、`event_type=gateway.access`：

- `event_id` 每个事件唯一，失败重投复用；`request.id` 由网关生成并返回 `X-Request-ID`，不信任调用方 ID 以免重复去重。
- `observed_at`、`started_at_epoch_ms`、`instance_id`、`gateway_provider=DATASCALPEL`。
- `service/route.{id,name,external_id}`；`consumer.{id,username,custom_id}`。只有 DATASCALPEL 来源输出外部关联 ID。
- `request.{id,method,path,size_bytes}`、`response.{status,size_bytes}`、`latencies_ms.{request,gateway,proxy,receive}`、`client_ip`、`upstream_status`。
- 路径只记录路由模板；未匹配请求使用 `/_unmatched` 和 Service `unmatched`。不记录动态路径实参、查询参数、请求/响应正文、原始 Header、Key、Authorization 或 Cookie。
- 正常代理在流完成后计时，客户端取消记录 499；已提交的响应中途失败记录 502（日志状态，不能修改已发出的 HTTP 状态）。未知长度为 null，不伪造流量。

独立有界队列默认 10000；非阻塞入队，专用发送线程与 Kafka Producer 隔离；最多追加两次有界重投。
Kafka 不可用不会阻塞代理；持续故障、队列满或停机可丢日志，**不是持久化可靠审计**。
使用 `acks=all`；request timeout 5000 ms、delivery timeout 10000 ms、发送线程 max.block 1000 ms。

`GET /admin-api/v1/runtime/telemetry` 返回：

- `traffic`：本节点启动时间、累计请求/5xx/拒绝数、滚动时间窗近似 P95/P99、堆使用/上限字节、进程 CPU 比例（无法读取为 null）、最新 100 条安全明细。
- `delivery`：enabled、topic、queued/capacity、delivered、failures（发送尝试失败，可能重试成功）、dropped、lastDeliveredAt。

网关 Web 运行节点页面每 5 秒刷新上述数据及配置修订。节点统计随重启清空，不能作为全网关历史统计；长周期历史由外部日志消费者维护。
Micrometer 现有 metrics 接口继续可用；本次没有新增 Prometheus 依赖或外部告警平台。
主动探活、故障摘除/多上游权重、自动告警通知、通用 CORS/Header 改写不在此实现内。

## 管理边界

原创建/发布/授权/撤回契约保留。托管对象在 Web 中引导至来源系统维护，避免手工造成漂移；原管理 API 的管理员权限模型不改变。
新增保护策略和有效期写接口对托管对象另要求机器认证；机器 Token 仍具有完整管理权限，应限制控制面网络暴露。
非 local/test 禁止默认管理密码、长度不足 12 的密码、默认 JWT 密钥、默认或长度不足 32 的机器 Token；JWT 长度检查沿用原实现。
