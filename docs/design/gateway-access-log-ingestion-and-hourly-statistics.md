# 网关访问日志接收与小时统计

## 1. 目标与边界

DataScalpel Admin 从 Kafka 接收由网关日志链路生成的规范化访问事件，在 PostgreSQL 中保存
7 天明细，并生成按服务、消费者和服务组合的小时统计。

当前日志链路：

```text
Kong http-log
-> Vector 白名单清洗
-> Kafka datascalpel.gateway.access.v1
-> DataScalpel Admin
-> PostgreSQL 明细与小时汇总
```

访问日志用于查询、统计、运维看板和短期审计，不参与鉴权、实时限流、配额扣减、计费或调用请求
的数据面。当前不实现 Prometheus、告警、DLQ 和 Kafka Topic 自动创建。

## 2. Kafka 契约和消费语义

Vector 将 Kong 的 HTTP 批次数组拆为独立 Kafka 事件。每个 Kafka Record 表示一次网关调用，
Kafka Key 必须等于事件中的 `event_id`。

默认配置：

```yaml
data-scalpel:
  gateway-access:
    enabled: false
    topic: datascalpel.gateway.access.v1
    consumer-group: data-scalpel-admin-gateway-access-v1
    concurrency: 2
    max-poll-records: 500
    raw-retention: 7d
    hourly-retention: 180d
```

消费者使用独立的批量 Listener Factory 和 `BATCH` 确认模式，不修改任务执行事件使用的全局
`record` 确认模式。一批规范化事件在一个短事务中使用 JDBC Batch 写入；数据库失败时不提交
Offset，每 5 秒重试。格式错误只丢弃当前消息，不阻塞同批有效事件。

支持的消息版本为：

```json
{
  "schema_version": "1.0",
  "event_type": "gateway.access",
  "event_id": "d5ca7632-fd2a-47cc-96b7-ab3e467548b6",
  "observed_at": "2026-07-27T08:00:00Z",
  "gateway_provider": "KONG",
  "started_at_epoch_ms": 1785110400000,
  "service": {
    "id": "kong-service-id",
    "name": "datascalpel-service-00000000-0000-0000-0000-000000000000"
  },
  "route": {
    "id": "kong-route-id",
    "name": "datascalpel-route-00000000-0000-0000-0000-000000000000"
  },
  "consumer": {
    "id": "kong-consumer-id",
    "custom_id": "00000000-0000-0000-0000-000000000001",
    "username": "example-client"
  },
  "credential": {
    "external_id": "kong-key-auth-credential-id"
  },
  "request": {
    "id": "kong-request-id",
    "method": "POST",
    "path": "/open-api/v1/example",
    "size_bytes": 256
  },
  "response": {
    "status": 200,
    "size_bytes": 1024
  },
  "latencies_ms": {
    "request": 18,
    "kong": 1,
    "proxy": 16,
    "receive": 1
  },
  "client_ip": "10.0.0.10",
  "upstream_status": "200"
}
```

`schema_version`、`event_type`、Event ID、Request ID、Service、调用时间和响应状态是必填字段。
调用时间早于接收时间 7 天或晚于接收时间 10 分钟的事件会被丢弃。请求路径在入库前再次移除
`?` 后内容并限制为 2048 字符。

## 3. 身份解析和安全边界

Kong 身份映射：

| 网关字段 | 本地字段 |
|---|---|
| `service.name=datascalpel-service-{UUID}` | `dataServiceId` |
| `route.name=datascalpel-route-{UUID}` | 与 Service 中的 ID 交叉校验 |
| `consumer.custom_id` | `consumerId` |
| `consumer.username` | Consumer code 日志快照 |
| `credential.external_id` | 网关 Credential 外部 ID |

身份状态：

- `RESOLVED`：服务和 Consumer 均可解析；
- `ANONYMOUS`：服务有效，Consumer 为空；
- `CONSUMER_UNRESOLVED`：Consumer 标识存在但不是 UUID；
- `SERVICE_UNRESOLVED`：无法从 Service 名称解析服务；
- `IDENTITY_MISMATCH`：Service 和 Route 指向不同 DataService。

消费阶段不逐条查询服务、Consumer、Credential 或订阅表。访问事实只保存稳定业务 ID 和网关
外部 ID，不建立业务外键，也不保存 `subscriptionId`。

数据库不保存完整 Kafka JSON、请求或响应 Body、Header、API Key、Authorization、Cookie 和
查询参数。客户端 IP 只保存在 7 天原始明细中，不进入小时汇总。

## 4. 数据模型和幂等

### 4.1 原始日志

表 `ds_gateway_access_log` 保存规范化字段、身份解析结果、流量和延迟以及 Kafka
Topic/Partition/Offset。

幂等约束：

- `event_id` 唯一，阻止同一 Kafka 事件重投；
- `gateway_provider + gateway_request_id` 唯一，阻止同一网关请求被重复日志插件发送。

主要查询索引：

- `occurred_at`；
- `data_service_id + occurred_at`；
- `consumer_id + occurred_at`。

实体由 Hibernate 管理结构，实际高吞吐写入使用 `JdbcTemplate.batchUpdate` 和
`ON CONFLICT DO NOTHING`。

### 4.2 小时状态和汇总

- `ds_gateway_access_hour_state`：记录小时的 `DIRTY / SUCCEEDED / FAILED` 状态；
- `ds_gateway_access_service_hourly`：每个 Provider、DataService、小时一行；
- `ds_gateway_access_consumer_service_hourly`：每个 Provider、DataService、Consumer、小时一行。

匿名调用只进入服务汇总。Service ID 无法解析或 Service/Route 不一致的日志保留在明细中，
但不进入业务汇总。

小时字段包括请求数、2xx/3xx/4xx/5xx、401/403/429、网关拒绝、网关错误、上游错误、请求和
响应字节数，以及 Request/Proxy 延迟的样本数、总和、最大值、P95 和 P99。

## 5. 归档与清理

每小时第 5 分钟按 UTC 选择已经结束的 `DIRTY/FAILED` 小时，每次最多处理 24 个。每个小时
在独立数据库事务中锁定状态，删除旧汇总，通过 PostgreSQL `INSERT ... SELECT ... GROUP BY`
和 `percentile_cont` 重建两类汇总，成功后写 `SUCCEEDED`。

原始日志插入和小时 `DIRTY` 标记在同一事务中。迟到日志会重新标脏已归档小时，下一轮执行
覆盖式重算，不进行增量累加。

每小时第 25 分钟分批删除 `occurred_at < now - 7 days` 的明细，默认每批 10,000 行、每轮
最多 100 批。每天清理 180 天前的小时汇总和小时状态。原始保留期严格优先，即使归档持续失败，
明细到期仍会删除并记录 WARN。

第一版本使用普通 PostgreSQL 表。当 7 天记录超过约 5,000 万行、持续流量超过约
100～200 RPS，或清理和 autovacuum 无法追上写入时，需要另行设计日分区迁移。

## 6. 查询 API

所有接口要求 `service.view`。

| 方法 | 地址 | 行为 |
|---|---|---|
| `GET` | `/api/v1/gateway-access-logs` | 查询 7 天内明细，默认最近 1 小时，单页最多 200 条 |
| `GET` | `/api/v1/gateway-access-statistics/overview` | 查询最近完整小时范围内的汇总概览 |
| `GET` | `/api/v1/gateway-access-statistics/hourly` | 按当前服务和 Consumer 范围查询聚合小时趋势 |
| `GET` | `/api/v1/gateway-access-statistics/services/hourly` | 按 DataService 查询小时趋势 |
| `GET` | `/api/v1/gateway-access-statistics/consumers/hourly` | 按 Consumer 查询小时趋势，可限定 DataService |
| `GET` | `/api/v1/gateway-access-statistics/rankings` | 按服务或 Consumer 查询调用量、5xx 或 P95 排行 |

明细接口的 `abnormalOnly=true` 表示只返回状态码不低于 400，或带有网关拒绝、网关错误、上游
错误标记的调用。查询响应通过左连接当前业务表补充服务和 Consumer 名称；这些连接不改变访问
日志表不建立业务外键的约束，主体已删除时调用事实和 UUID 仍然可查。

通用小时趋势和排行支持可选的 `dataServiceId`、`consumerId`，用于运维页面多维联动。排行
响应同时返回主体当前名称、编码和 2xx/4xx/5xx 数量；主体已删除时名称和编码为空。

明细最大查询范围为 7 天，统计最大查询范围为 180 天。统计时间窗口统一归一化为 UTC
完整小时，响应返回实际的 `fromInclusive` 和 `toExclusive`。

小时表中的 P95/P99 是单小时精确值。概览和跨小时排行返回字段名为
`peakHourlyRequestLatencyP95Ms/P99Ms`，表示查询窗口内最差小时分位值，不伪装成跨小时原始
样本的整体分位数。通用趋势在跨服务或跨 Consumer 合并时也采用底层小时分组最大分位值，
字段名使用 `peakGrouped...` 明确该口径。

## 7. 运维统计页面

前端入口为“数据服务 → 调用统计”，路由 `/dataservice/operations`，要求 `service.view`
权限。页面的交互和统计口径见
[网关运维统计页面](gateway-operations-dashboard.md)。
