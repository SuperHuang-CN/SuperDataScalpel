# API 消费者、凭证与服务订阅管理

## 1. 目标与边界

API Consumer 表示调用已发布数据服务的外部应用或系统，与 DataScalpel 后台登录用户完全分离。Consumer 不引用系统用户、角色或登录凭证，也不参与后台 JWT 认证。

当前提供：

- 创建消费者；
- 分页查询消费者；
- 修改名称和说明；
- 将消费者重新同步到当前网关；
- 从网关和 DataScalpel 删除消费者；
- 创建、查询、轮换和删除 Consumer API Key；
- 为 Consumer 创建、查询、重新同步和撤回数据服务订阅；
- 在 Consumer 和 DataService 两个管理视角展示凭证、订阅与网关状态。

当前不提供 JWT、Basic Auth、访问配额、限流策略、调用统计和调用审计。凭证和订阅分别使用独立资源及网关端口，不塞入 Consumer CRUD 接口。详细开发与验收计划见 [API 消费者凭证与服务订阅开发计划](api-consumer-service-subscription-development-plan.md)。

## 2. 主数据和流量边界

DataScalpel 是消费者主数据的权威来源，网关是可重新同步的执行端。网关中的对象丢失后，可以根据 DataScalpel 主数据恢复；DataScalpel 不从网关反向导入未知消费者。

Consumer、API Key 和服务订阅的只读手动对账、漂移状态与显式修复动作见
[手动网关状态对账与显式重新同步](manual-gateway-reconciliation.md)。

业务请求的数据面流量直接进入网关代理地址，再由网关转发到 Service Engine。DataScalpel Admin 只通过网关管理地址执行低频控制面操作，运行时流量不会经过 Spring MVC 消费者管理接口。

```text
管理界面 -> DataScalpel Admin -> Gateway Admin API

API 调用方 -> Gateway Proxy -> Service Engine
```

## 3. 数据模型

### 3.1 ApiConsumer

表：`ds_api_consumer`

| 字段 | 约束 |
|---|---|
| `id` | UUID 主键 |
| `code` | 全局唯一、创建后不可修改，2～64 位 |
| `name` | 必填，最长 100 |
| `description` | 可选，最长 1000 |
| `revision` | 创建为 1，名称或说明修改后递增 |
| `createdAt` / `updatedAt` | 统一审计时间 |

`code` 必须以小写字母开头，只允许小写字母、数字、点、下划线和连字符。它是 DataScalpel 与不同网关之间的稳定业务标识。

### 3.2 GatewayConsumerBinding

表：`ds_gateway_consumer_binding`

一个 Consumer 可以拥有多个 Provider Binding，唯一约束为 `consumerId + provider`。

| 字段 | 含义 |
|---|---|
| `consumerId` | Consumer UUID 标量引用 |
| `provider` | 网关实现类型 |
| `externalId` | 网关返回的消费者 ID |
| `syncedRevision` | 最近成功同步的 Consumer revision |
| `syncStatus` | 当前同步或删除状态 |
| `lastError` | 最近一次安全、限长的错误 |
| `operationStartedAt` | 当前控制面操作开始时间 |
| `lastSyncedAt` | 最近成功同步时间 |

状态包括：

- `SYNC_PENDING`
- `SYNCED`
- `SYNC_FAILED`
- `DELETE_PENDING`
- `DELETE_FAILED`

近期仍处于 Pending 的操作拒绝重叠执行；超过操作保护窗口后允许人工重试，用于恢复进程中断遗留的状态。

### 3.3 API Key Credential

`ApiConsumerCredential` 归属于 Consumer，一个 Consumer 可以拥有多个命名 Key。Key
由 DataScalpel 生成，数据库只保存 SHA-256 摘要和脱敏提示，明文只在网关同步成功的创建或轮换响应中返回一次。

`GatewayCredentialBinding` 以 `credentialId + provider` 唯一，保存外部凭证 ID、已同步 revision、
同步状态、`operationId` 和安全错误。所有有效 Key 识别为同一个 Consumer，因此共享该 Consumer
的全部服务订阅。

### 3.4 服务订阅

`ApiServiceSubscription` 是 Consumer 与 DataService 之间不可修改的唯一关系，数据库约束
`consumerId + dataServiceId` 唯一。`desiredState` 表达 DataScalpel 希望网关收敛到
`GRANTED` 或 `REVOKED`。

`GatewaySubscriptionBinding` 以 `subscriptionId + provider` 唯一，保存网关授权对象 ID 和
`GRANT_PENDING / GRANTED / GRANT_FAILED / REVOKE_PENDING / REVOKE_FAILED` 状态。
通用模型不保存 Kong ACL Group；Provider 专属名称只在对应适配器内计算。

## 4. 薄网关抽象

网关能力使用职责单一的专用端口，不建立包含所有能力的万能 Client：

```java
public interface GatewayConsumerPort {

    GatewayProvider provider();

    GatewayConsumerResult upsert(GatewayConsumerSpec consumer);

    void remove(GatewayConsumerReference consumer);
}
```

`GatewayConsumerPortRegistry` 根据 `data-scalpel.service-gateway.provider` 选择当前写入 Provider，并能根据历史 Binding 中保存的 Provider 找到对应删除适配器。

凭证和订阅分别使用 `GatewayCredentialPort`、`GatewaySubscriptionPort`，服务发布使用
`GatewayServicePort`。当前生产适配实现 `KONG`；枚举预留 `APISIX` 和 `DATASCALPEL`，
但没有在缺少真实需求时提前实现它们。

## 5. Kong 映射和归属保护

Kong Consumer 映射：

| DataScalpel | Kong |
|---|---|
| `ApiConsumer.code` | `username` |
| `ApiConsumer.id` | `custom_id` |
| 系统归属 | `tags` |

写入以下 Tag：

- `datascalpel`
- `datascalpel-consumer`
- `datascalpel-consumer-{consumerId}`

同步行为：

1. 按 Consumer code 查询 Kong。
2. 不存在时创建。
3. 已存在且 `custom_id` 等于 DataScalpel Consumer ID 时更新。
4. 同名但 `custom_id` 不一致时拒绝接管。
5. 创建返回 409 时重新读取并校验归属，支持并发幂等创建。

删除行为：

1. 优先按 Binding 保存的 `externalId` 查询。
2. 删除前校验 `custom_id`。
3. 归属不匹配时拒绝删除。
4. 查询或删除返回 404 时视为幂等成功。

第一阶段 Kong Admin API 不发送认证 Header。生产环境应通过内网隔离、网络策略或后续明确的管理面认证机制保护 Admin API。

Kong API Key 使用 Consumer 的 `key-auth` Credential；订阅使用 Consumer 的 ACL
membership。受保护服务的 ACL Group 使用稳定名称：

```text
datascalpel-service-{dataServiceId}
```

凭证和订阅都写入 DataScalpel 归属 Tag。创建冲突后重新读取并校验归属，撤回或删除遇到
404 视为幂等成功；归属不匹配时拒绝接管或删除。

## 6. 事务和失败语义

外部 HTTP 调用不得位于管理数据库长事务内。创建、修改、同步采用：

```text
短事务：保存主数据并写 SYNC_PENDING
-> 事务外：调用 GatewayConsumerPort.upsert
-> 短事务：写 SYNCED 或 SYNC_FAILED
```

创建或修改时，即使网关同步失败，也保留 DataScalpel 主数据和失败状态，接口返回当前 Consumer，管理端提示“已保存但同步失败”。用户可以通过同步 Action 重试。

删除采用：

```text
短事务：所有 Binding 写 DELETE_PENDING
-> 事务外：逐个删除网关消费者
-> 短事务：删除成功的 Binding；失败的写 DELETE_FAILED
-> 所有 Binding 删除后再删除 Consumer
```

网关删除失败返回 HTTP 502 Problem Detail，并保留 Consumer 和失败 Binding，确保后续可重试。多个 Binding 部分成功时，已成功清理的 Binding 不回滚，重试只处理剩余 Binding。

Credential 和 Subscription 同样采用“短事务 Pending → 事务外网关调用 → 短事务完成”的流程。
每次操作使用独立 `operationId`，旧 HTTP 调用完成后不能覆盖更新的操作状态。授权失败保留订阅
意图并返回带 `GRANT_FAILED` Binding 的订阅 DTO；撤回失败可能意味着权限仍然存在，因此返回
502 并保留 `REVOKED + REVOKE_FAILED` 供重试。

## 7. REST API

Consumer 查询需要 `service.view`，Consumer CRUD 使用 `service.update`。凭证和订阅查询使用
`service.view`，其写入、同步、轮换和撤回会改变网关暴露行为，使用 `service.publish`。

| 方法 | 地址 | 行为 |
|---|---|---|
| `GET` | `/api/v1/api-consumers` | 使用统一 Search DSL 分页查询 |
| `GET` | `/api/v1/api-consumers/{id}` | 查询详情 |
| `POST` | `/api/v1/api-consumers` | 创建并同步 |
| `POST` | `/api/v1/api-consumers/{id}/actions/update` | 修改名称/说明并同步 |
| `POST` | `/api/v1/api-consumers/{id}/actions/sync` | 手工重新同步 |
| `POST` | `/api/v1/api-consumers/{id}/actions/delete` | 删除网关对象和本地主数据 |

凭证：

| 方法 | 地址 | 行为 |
|---|---|---|
| `GET` | `/api/v1/api-consumers/{consumerId}/credentials` | 查询脱敏凭证 |
| `POST` | `/api/v1/api-consumers/{consumerId}/credentials` | 创建并一次性返回明文 |
| `POST` | `/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/rotate` | 轮换并一次性返回新明文 |
| `POST` | `/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/delete` | 删除网关和本地凭证 |

订阅：

| 方法 | 地址 | 行为 |
|---|---|---|
| `GET` | `/api/v1/api-service-subscriptions` | 分页查询，可按 Consumer、DataService 过滤 |
| `GET` | `/api/v1/api-service-subscriptions/{id}` | 查询详情 |
| `POST` | `/api/v1/api-service-subscriptions` | 创建订阅并授权 |
| `POST` | `/api/v1/api-service-subscriptions/{id}/actions/sync` | 重新同步授权 |
| `POST` | `/api/v1/api-service-subscriptions/{id}/actions/revoke` | 撤回授权并删除订阅 |

成功响应直接返回 `ApiConsumerResponse`、`PageResponse<ApiConsumerResponse>` 或空响应；错误统一采用 RFC 9457 `application/problem+json`。

## 8. 配置

默认配置关闭网关写入，避免影响未接入网关的环境：

```yaml
data-scalpel:
  service-gateway:
    provider: ${DATASCALPEL_SERVICE_GATEWAY_PROVIDER:none}
    kong:
      admin-url: ${DATASCALPEL_KONG_ADMIN_URL:}
      proxy-url: ${DATASCALPEL_KONG_PROXY_URL:}
      connect-timeout: ${DATASCALPEL_KONG_CONNECT_TIMEOUT:3s}
      request-timeout: ${DATASCALPEL_KONG_REQUEST_TIMEOUT:5s}
```

`provider=none` 时查询仍可使用；需要同步网关的命令返回 503。`proxy-url` 第一阶段不参与消费者控制面调用，为后续服务路由接入和调用地址展示保留。

当前工作区的 `config/application-local.yml` 默认启用：

```yaml
data-scalpel:
  service-gateway:
    provider: kong
    kong:
      admin-url: http://10.0.0.5:8003
      proxy-url: http://10.0.0.5:8000
```

该本地配置被 `.gitignore` 排除，不进入提交和构建产物。

## 9. 本地验证

工程本地功能测试优先使用 `config/application-local.yml`。根目录的 `start-local-dev.sh` 会对 Admin、Service Engine 和 Task Dispatcher 显式传入：

```text
--spring.profiles.active=local
--spring.config.additional-location=optional:file:<工程根目录>/config/application-local.yml
```

Task Engine 不是 Spring Boot 应用，不读取该 YAML。

启动前先检查 Kong 管理面：

```bash
curl --fail --silent --show-error http://10.0.0.5:8003/status
```

然后执行：

```bash
./start-local-dev.sh
```

在“数据服务 / 消费者管理”中依次验证 Consumer、API Key、服务发布、订阅和撤回，并可直接检查 Kong：

```bash
curl --fail --silent --show-error http://10.0.0.5:8003/consumers/<consumer-code>
```

受保护调用链应验证：无 Key 返回 401、有效 Key 未订阅返回 403、订阅后返回 200、撤回后再次返回 403。

自动化 Kong Adapter 测试使用本机临时 JDK `HttpServer`，不依赖真实 Kong；真实地址不可达时只能证明适配器协议行为，不能替代环境验收。
