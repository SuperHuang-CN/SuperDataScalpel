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

- Service 使用 DataService code/name、Service Engine `runtimeUrl` 与固定
  `/runtime/v1/services/{dataService.id}` 上游路径，以及访问模式；
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
