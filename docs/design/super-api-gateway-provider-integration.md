# Super API Gateway Provider 集成

## 1. 目标与边界

DataScalpel 使用现有 `GatewayProvider.DATASCALPEL` 对接同仓但完全独立的 Super API
Gateway。DataScalpel 是 Service、Consumer、API Key 和 Subscription 的主数据来源，
网关只保存代理所需的执行态副本。

两个工程只通过 `/admin-api/v1` HTTP 契约通信：

- DataScalpel 不读取 `super_api_gateway` schema，也不依赖网关源码或 DTO；
- Super API Gateway 不读取 DataScalpel 表、配置或登录态；
- 管理调用使用 `X-Super-Gateway-Admin-Token`；
- 业务请求直接进入网关 Proxy，不经过 DataScalpel Admin；
- 系统保持单活动 Provider，不执行 Kong 与自研网关双写。

## 2. 配置

默认 Provider 仍为 `NONE`。启用自研网关时配置：

```yaml
data-scalpel:
  service-gateway:
    provider: datascalpel
    super-api-gateway:
      admin-url: http://localhost:19000
      proxy-url: http://localhost:19000
      machine-token: ${DATASCALPEL_SUPER_API_GATEWAY_MACHINE_TOKEN:}
      connect-timeout: 3s
      request-timeout: 5s
      upstream-connect-timeout: 3s
      upstream-response-timeout: 35s
```

`admin-url` 和 `proxy-url` 当前可以相同，但保持独立配置，以支持未来控制面与数据面使用
不同入口。生产环境必须通过 HTTPS 或等效内网安全边界保护 Machine Token。

## 3. 外部引用与资源映射

所有 DataScalpel 管理对象使用固定 `source=DATASCALPEL`：

| 本地对象 | 网关对象 | `externalId` |
| --- | --- | --- |
| DataService | Service | DataService UUID |
| DataService | Route | DataService UUID |
| ApiConsumer | Consumer | Consumer UUID |
| ApiConsumerCredential | API Key | Credential UUID |
| ApiServiceSubscription | Subscription | Subscription UUID |

具体配置：

- Service 使用 DataService code/name、Service Engine `runtimeUrl` 与
  `dataService.contextPath` 上游路径，以及访问模式；
- Route 使用相同 code/name、Gateway Binding 的公开路径、`POST`、`order=0` 和
  `stripPrefixSegments=0`，并通过 `upstreamPath` 固定转发到 Engine 内部路由；
- Consumer 使用本地 code/name/description 并保持启用；
- API Key 名称为 `ds-key-{credentialId}`，明文由 DataScalpel 生成；
- Subscription 使用远端 Consumer UUID 和 Service UUID；
- 发布结果中的网关地址由 `proxy-url + routePath` 生成。

适配器先按 `source + externalId` 精确读取。创建遇到 409 时重新读取并校验归属；
如果 code、路径或关联对象属于其他来源，则拒绝接管。

## 4. Service 发布和撤回

发布采用 fail-closed 顺序：

```text
读取并校验已有对象
-> 停用已有 Route
-> upsert 禁用 Service
-> upsert 禁用 Route
-> 启用 Service
-> 最后启用 Route
-> 重新读取并校验
```

只有最终 Service 和 Route 的外部 ID、上游、访问模式、超时、路径、方法、order、strip
及启停状态全部一致时，本地 Binding 才进入 `PUBLISHED`。

取消发布必须保留 DataScalpel 已有订阅语义：

1. 停用并删除 Route，立即终止外部访问；
2. 停用 Service；
3. 没有 ACTIVE Subscription 时物理删除 Service；
4. 仍有订阅时保留禁用 Service，作为 Subscription 的稳定身份锚点；
5. 重新发布按外部引用复用 Service UUID 并重建 Route；
6. 最后一个 Subscription 撤回后再次尝试删除禁用 Service。

因此对账中的“已撤回”不要求 Service 必然不存在，只要求 Route 不可访问且 Service
未启用。

## 5. Consumer、API Key 和 Subscription

Consumer 创建与更新按外部引用收敛；删除时先停用再删除。DataScalpel 已经要求先清理
API Key 和 Subscription，因此不会依赖网关的级联删除完成业务校验。

API Key 明文始终由 DataScalpel 生成：

- 不存在时调用创建接口并提交 `secret`；
- 已存在时调用 rotate 并提交同一 `secret`；
- 网关只保存 SHA-256、前缀和末四位，调用方提供的明文不会在响应中回显；
- 对账读取 Key 详情的 `secretDigest` 与本地摘要比较；
- 远端缺失或摘要不一致只能通过轮换修复。

Subscription 授权必须同时校验外部引用以及远端 Consumer/Service 关系。撤回顺序为：

```text
actions/revoke
-> actions/delete
-> 尝试清理没有其他订阅的禁用 Service
```

物理删除保证同一 Consumer 后续可以用新的本地 Subscription UUID 重新订阅同一 Service。

## 6. 错误、事务与对账

Super API Gateway 的 RFC 9457 `code/detail` 被转换为现有四类 Provider
OperationException。404 在删除链路中视为幂等成功；409 只用于并发收敛、归属冲突或保留
仍有订阅的禁用 Service。错误内容限制长度，不保存 Token、Key、请求体或未知 Header。

现有业务编排继续使用：

```text
短事务写 Pending
-> 事务外调用网关
-> 短事务写成功或失败
```

四类 `inspect` 分别检查 Service/Route、Consumer、Key 摘要和 Subscription 关联关系。
对账只保存诊断，不修改远端；修复继续使用 publish、sync、rotate、revoke 等显式 Action。

## 7. 从 Kong 切换

切换需要维护窗口，不提供自动批量迁移：

1. 启动 Super API Gateway 并确认节点 revision 健康；
2. 从 Kong 取消发布全部 DataService；
3. 将活动 Provider 改为 `DATASCALPEL` 并重启 Admin；
4. 同步 Consumer；
5. 发布 DataService；
6. 轮换全部 API Key 并重新交付调用方；
7. 同步 Subscription；
8. 完成四类手动对账和真实调用后切换 Proxy 入口。

DataScalpel 不保存 Key 明文，因此旧 Key 无法复制到新网关。Key 轮换后的回退同样需要在
Kong 下再次轮换并重新交付。

## 8. 保护策略、有效期与统计（2026-09-29）

本轮不改变数据服务定义、Engine 发布过程或 Provider 切换流程；不适配 APISIX。

- Admin 服务详情增加“网关保护与监控”；通过公开 HTTP 操作自研网关的服务限流、Consumer 限流、并发、请求体大小和 IP/CIDR 策略。
- Admin 消费者访问配置的 Key/订阅菜单增加有效期、续期；订阅可单独设置每秒额度。保持“Consumer 持有 Key，Subscription 授权具体服务”，不是每个服务共用一把 Key，也未增加用户自助申请/审批门户。
- Admin `/api/v1/data-services/{id}/gateway-traffic-policy` 查询，`.../actions/update-gateway-traffic-policy` 保存；`/api/v1/gateway-access-validities/{kind}/{id}` 查询，`.../actions/update` 保存。读需要 service.view，写需要 service.publish。id 均为本地对象 UUID，先校验 source + externalId 再调用远端。
- 这些新增**网关运行策略**以网关持久化配置为准，Admin 实时读取和下发，不另维护一份本地 desired config；已有 Service/Key/Subscription 主数据来源仍是 Admin。现存网关对象重同步不会清空策略；对象删除重建、撤回后重新申请或切换 Provider 不自动迁移策略，必须重新配置。需随网关 schema 一起备份，不能只备份 Admin。
- 返回的 loadedRevision 是响应节点修订，不表示集群所有节点均已加载；全节点收敛须查看网关运行节点列表。未适配 Provider 返回 501；未同步/归属冲突 409；非法参数 400；远端失败/结果未确认 502，不能显示成保存成功。
- 限流、并发和订阅额度均为单节点，不宣称分布式全局限额；现有服务默认为无限制、无自动到期。

访问日志统一规范化 v1 事件和 `datascalpel.gateway.access.v1` topic。自研网关用 `service/route.external_id`、`consumer.custom_id` 关联本地主体；Kong 保持原命名解析。旧 `kongLatencyMs` 字段兼容保留，自研网关映射其自身延迟，页面统一称“网关自身延迟”。旧版平铺事件不能恢复缺失日志。
Admin 必须启用 `data-scalpel.gateway-access.enabled`，并与网关使用相同 Kafka broker/topic。调用必须经过网关，直接访问 Service Engine 不产生网关统计。
新增最近十五分钟原始日志实时统计，包含当前小时；现有已完成小时趋势和排行不改口径，两者不相加。

独立网关详细字段和边界见其 [保护与可观测性](../../super-api-gateway/docs/gateway-protection-and-observability.md)；页面说明见 [运维统计](gateway-operations-dashboard.md)。
