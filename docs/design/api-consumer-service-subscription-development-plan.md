# API 消费者凭证与服务订阅开发计划

## 1. 目标

本计划在现有 API Consumer 管理、数据服务启停和网关发布能力上，实现一条可真实执行的受保护 API 调用链：

```text
Consumer 创建 API Key
-> 数据服务声明 SUBSCRIPTION_REQUIRED
-> 发布时配置网关认证和 ACL
-> Consumer 订阅服务
-> 携带 API Key 的请求经过认证和授权后进入 Service Engine
```

DataScalpel 是 Consumer、Credential、Subscription 和访问模式的主数据来源。Kong、APISIX 或自研网关是可以重新同步的执行端。业务流量不经过 DataScalpel Admin。

## 2. 交付范围

本阶段交付：

1. 数据服务访问模式 `PUBLIC`、`SUBSCRIPTION_REQUIRED`；
2. Provider 无关的 API Key 凭证端口及 Kong `key-auth` 实现；
3. Provider 无关的订阅授权端口及 Kong ACL membership 实现；
4. Kong 受保护服务发布时的 `key-auth + acl` 插件配置；
5. Consumer 凭证创建、轮换、查询和删除；
6. Consumer 对数据服务的订阅、重试同步、查询和撤回；
7. Consumer、DataService 删除时的订阅引用保护；
8. 前端服务访问模式、Consumer 凭证和订阅管理；
9. 自动化测试和真实 Kong 401/403/200/撤回验证。

本阶段不交付：

- JWT、Basic Auth 等其他调用凭证；
- 限流、配额、计费和调用统计；
- Consumer 或订阅逻辑删除；
- Provider 切换后的自动大批量迁移任务；
- Kong Admin API 认证。

## 3. 核心约束

### 3.1 访问模式

新增 `DataServiceAccessMode`：

| 值 | 行为 |
|---|---|
| `PUBLIC` | 保持当前无调用方认证的兼容行为 |
| `SUBSCRIPTION_REQUIRED` | 必须通过 API Key 认证并拥有服务订阅 |

- 新建服务默认 `PUBLIC`；
- 只有 `DRAFT`、`DISABLED` 服务允许修改；
- `SUBSCRIPTION_REQUIRED` 服务仍有订阅时，不允许改为 `PUBLIC`；
- `SUBSCRIPTION_REQUIRED` 服务只有认证、ACL 插件和 Route 全部同步成功后才标记为网关 `PUBLISHED`；
- 受保护服务发布必须 fail-closed，不能留下缺少插件的公开 Route。

### 3.2 API Key

- 一个 Consumer 可以创建多个命名 API Key，便于无中断轮换；
- API Key 归属于 Consumer，所有有效 Key 共享该 Consumer 的服务订阅；
- 创建订阅不要求 Consumer 已经创建 API Key，凭证生命周期与授权生命周期相互独立；
- DataScalpel 使用安全随机数生成 Key，只持久化不可逆摘要和脱敏提示，不持久化明文；
- 明文只在创建或轮换成功响应中返回一次；
- 网关同步失败时保留本地 Credential 和失败 Binding，用户通过轮换生成新 Key 重试；
- 删除时先清理所有 Provider Binding，再删除本地 Credential。

### 3.3 订阅

- 一个 Consumer 对一个 DataService 最多一条订阅；
- 只有当前 Provider Consumer 已同步、服务已发布且访问模式为 `SUBSCRIPTION_REQUIRED` 时允许订阅；
- 授权失败保留本地订阅意图并进入 `GRANT_FAILED`，缺少 ACL membership 时请求仍被拒绝；
- 创建授权失败仍返回已创建的订阅 DTO，由 `GRANT_FAILED` 明确表达“意图已保存但尚未生效”；
- 撤回失败保留 Subscription 和失败 Binding，并返回 502；
- Service 停用保留订阅和 ACL membership，重新发布到同一 Provider 后自动恢复；
- Consumer 或 Service 存在订阅时禁止删除，必须先显式撤回。

## 4. 数据模型

### 4.1 ApiConsumerCredential

表 `ds_api_consumer_credential`：

| 字段 | 含义 |
|---|---|
| `id` | UUID |
| `consumerId` | Consumer UUID 标量引用 |
| `name` | 凭证用途名称 |
| `secretDigest` | SHA-256 摘要，仅用于本地主数据完整性 |
| `secretHint` | 脱敏提示 |
| `revision` | 创建为 1，每次轮换递增 |
| `createdAt` / `updatedAt` | 审计时间 |

### 4.2 GatewayCredentialBinding

表 `ds_gateway_credential_binding`，唯一约束 `credentialId + provider`：

- `externalId`
- `syncedRevision`
- `status`: `SYNC_PENDING / ACTIVE / SYNC_FAILED / DELETE_PENDING / DELETE_FAILED`
- `lastError`
- `operationId`
- `operationStartedAt`
- `lastSyncedAt`

### 4.3 ApiServiceSubscription

表 `ds_api_service_subscription`，唯一约束 `consumerId + dataServiceId`：

- `consumerId`
- `dataServiceId`
- `desiredState`: `GRANTED / REVOKED`
- `createdAt`
- `updatedAt`

### 4.4 GatewaySubscriptionBinding

表 `ds_gateway_subscription_binding`，唯一约束 `subscriptionId + provider`：

- `externalMembershipId`
- `status`: `GRANT_PENDING / GRANTED / GRANT_FAILED / REVOKE_PENDING / REVOKE_FAILED`
- `lastError`
- `operationId`
- `operationStartedAt`
- `grantedAt`

通用订阅模型不得保存 Kong ACL Group。Kong 使用的稳定 Group 名称由 Kong
适配器根据 `dataServiceId` 计算，避免 Provider 概念泄漏到业务层。

## 5. Provider 端口

凭证端口：

```java
public interface GatewayCredentialPort {

    GatewayProvider provider();

    GatewayCredentialResult upsert(GatewayCredentialSpec credential);

    void remove(GatewayCredentialReference credential);
}
```

订阅端口：

```java
public interface GatewaySubscriptionPort {

    GatewayProvider provider();

    GatewaySubscriptionResult grant(GatewaySubscriptionSpec subscription);

    void revoke(GatewaySubscriptionReference subscription);
}
```

Service、Consumer、Credential、Subscription 继续使用四个独立 Registry，不建立万能 Gateway Client。

## 6. Kong 映射

### 6.1 服务保护

ACL Group 使用稳定名称：

```text
datascalpel-service-{dataServiceId}
```

`SUBSCRIPTION_REQUIRED` 的 Kong Service 配置：

- Service 级 `key-auth` 插件；
- Service 级 `acl` 插件，`config.allow` 只包含稳定 ACL Group；
- `hide_credentials=true`；
- `hide_groups_header=true`；
- API Key 只允许通过 `X-API-Key` Header；
- 插件使用 DataScalpel、服务类型和服务 ID 标签。

发布顺序：

```text
upsert Service
-> 对已有但未保护的 Route 先删除，消除公开窗口
-> upsert/verify key-auth
-> upsert/verify acl
-> upsert Route
-> verify Service、plugins、Route
```

`PUBLIC` 服务只删除 DataScalpel 自己管理的认证和 ACL 插件，不接管或删除外部插件。

### 6.2 API Key

Kong Key Auth Credential：

- Consumer：当前 Provider 的 `GatewayConsumerBinding.externalId`；
- Key：DataScalpel 生成的一次性明文；
- Tag：`datascalpel`、`datascalpel-credential`、`datascalpel-credential-{credentialId}`。

创建并发 409 后重新读取；存在非 DataScalpel 同归属对象时拒绝接管。轮换使用稳定 Credential ID 找到并更新。

### 6.3 Subscription

Kong ACL membership：

- Consumer：当前 Provider Consumer 外部 ID；
- Group：`datascalpel-service-{dataServiceId}`；
- Tag：`datascalpel`、`datascalpel-subscription`、`datascalpel-subscription-{subscriptionId}`。

授权支持 409 并发幂等，撤回支持 404 幂等。删除前校验 Consumer、Group 和归属标签。

## 7. 控制面流程

所有流程统一为：

```text
短事务：锁定主数据并写 Pending
-> 事务外：调用网关
-> 短事务：依据操作令牌写成功或失败
```

近期 Pending 操作在 30 秒内拒绝重叠；超时允许人工重试。

### 7.1 创建/轮换 API Key

1. 校验 Consumer 当前 Provider Binding 为最新 `SYNCED`；
2. 生成至少 256 bit 随机 Key；
3. 保存摘要、提示和 `SYNC_PENDING`；
4. 事务外 upsert；
5. 成功写 `ACTIVE` 并只在本次响应返回明文；
6. 失败写 `SYNC_FAILED`，不返回明文。

### 7.2 创建订阅

1. 校验 Consumer 和受保护 DataService；
2. 校验同一 Provider 的 Consumer、Service Binding；
3. 保存 Subscription 和 `GRANT_PENDING`；
4. 事务外 grant；
5. 依据 `operationId` 写 `GRANTED` 或 `GRANT_FAILED`；
6. 授权失败保留订阅并返回包含失败 Binding 的 201 响应。

### 7.3 撤回订阅

1. 所有 Binding 写 `REVOKE_PENDING`；
2. 逐 Provider 事务外 revoke；
3. 成功删除 Binding，失败写 `REVOKE_FAILED`；
4. 所有 Binding 清理完成后删除 Subscription；
5. 部分失败返回 502 并允许重试。

## 8. REST API

凭证：

| 方法 | 地址 |
|---|---|
| `GET` | `/api/v1/api-consumers/{consumerId}/credentials` |
| `POST` | `/api/v1/api-consumers/{consumerId}/credentials` |
| `POST` | `/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/rotate` |
| `POST` | `/api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/delete` |

订阅：

| 方法 | 地址 |
|---|---|
| `GET` | `/api/v1/api-service-subscriptions` |
| `GET` | `/api/v1/api-service-subscriptions/{id}` |
| `POST` | `/api/v1/api-service-subscriptions` |
| `POST` | `/api/v1/api-service-subscriptions/{id}/actions/sync` |
| `POST` | `/api/v1/api-service-subscriptions/{id}/actions/revoke` |

读取使用 `service.view`；凭证和订阅写操作使用 `service.publish`。

## 9. 前端

### 9.1 服务管理

- 编辑页增加访问模式选择；
- 列表展示“公开访问/订阅访问”；
- 受保护服务网关发布失败时展示插件同步错误；
- cURL 对受保护服务增加 `X-API-Key` 占位 Header。

### 9.2 Consumer 管理

增加“访问配置”抽屉：

- API Key 列表：名称、脱敏提示、Provider 状态、轮换、删除；
- 新建/轮换成功后使用不可重复打开的 Modal 展示明文；
- 订阅列表：服务、网关状态、同步、撤回；
- 从已发布且受保护的服务中选择新增订阅。

### 9.3 服务订阅视角

数据服务列表提供“订阅消费者”入口，展示当前 Consumer 和授权状态，支持新增、同步和撤回。

## 10. 测试与验收

### 10.1 后端自动化

- AccessMode 创建、修改限制和兼容默认值；
- 存在订阅时禁止将受保护服务改为公开访问；
- API Key 明文只在成功创建/轮换响应中出现，Entity/普通查询不包含；
- Credential 网关失败、轮换恢复、删除失败恢复；
- Subscription 成功、重复、前置条件、授权失败恢复、撤回失败恢复；
- Consumer/Service 删除引用保护；
- 所有外部调用均在事务外；
- Kong Service 插件 fail-closed 发布顺序；
- Kong Credential/ACL 的创建、更新、409、404、归属冲突。

### 10.2 前端自动化

- AccessMode 表单和请求映射；
- API Key 一次性展示；
- Credential/Subscription 状态和错误展示；
- 新增、同步、轮换、撤回交互；
- 受保护服务 cURL Header。

### 10.3 完整验证

```bash
./mvnw verify
pnpm --dir data-scalpel-ui check
```

### 10.4 真实 Kong

1. 创建 Consumer 和 API Key；
2. 创建、启用并发布 `SUBSCRIPTION_REQUIRED` SQL 服务；
3. 无 Key 请求返回 401；
4. 有效 Key、未订阅返回 403；
5. 创建订阅后返回 200；
6. 撤回订阅后返回 403；
7. 删除 Credential、Consumer、Service 和所有临时业务/网关对象；
8. 恢复本地 Engine 配置。

## 11. 开发阶段与完成标准

### 阶段一：领域与契约

- 完成 AccessMode、Credential、Subscription 和各 Provider Binding；
- Consumer、Credential、Subscription、DataService 保持 UUID 标量引用；
- 数据库唯一约束和按服务反查订阅的索引生效；
- REST 仅使用 GET/POST，撤回使用 `actions/revoke`。

完成标准：模块编译通过，领域和 Repository 测试覆盖唯一性、不可修改关系和状态转换。

### 阶段二：网关适配

- 完成 Kong Consumer、Key Auth Credential、ACL membership 和 Service 插件适配；
- 所有写入具备稳定归属 Tag、并发 409 幂等和删除 404 幂等；
- 通用 Port 不包含 Kong Group、Plugin 等 Provider 专属字段；
- 受保护 Route 只在 `key-auth`、`acl` 全部验证后创建。

完成标准：Kong Adapter 自动化测试覆盖创建、更新、分页、冲突、归属保护和 fail-closed 顺序。

### 阶段三：应用服务与生命周期

- 外部 HTTP 调用全部位于数据库事务外；
- Pending 操作使用独立 `operationId` 防止旧结果覆盖；
- 创建失败保留本地意图，撤回失败保留可重试状态并返回 502；
- Consumer、Credential、Subscription、DataService 删除和访问模式切换具有引用保护。

完成标准：Spring 集成测试覆盖成功、失败恢复、重复请求、前置条件和引用保护。

### 阶段四：管理界面

- 服务编辑器、列表和 cURL 展示访问模式；
- Consumer 访问配置支持一次性 Key、轮换、删除、订阅、同步和撤回；
- DataService 列表支持从服务视角管理订阅消费者；
- 业务期望状态与网关实际状态分别展示。

完成标准：前端定向测试通过，并完成 `pnpm --dir data-scalpel-ui check`。

### 阶段五：文档和验收

- 同步 Consumer 管理、服务网关发布、README 和本计划；
- 根目录执行 `./mvnw verify`；
- 真实 Kong 完成 401、403、200、撤回后 403；
- 清理验收临时数据和 Kong 对象，恢复本地配置。

完成标准：完整构建和真实调用链均有可重复的验收记录。

## 12. 完成与验收记录

2026-07-25 已完成本计划的五个阶段：

- 根目录 `./mvnw verify` 通过，13 个 Maven 模块全部成功；
- `pnpm --dir data-scalpel-ui check` 通过，74 个测试文件、246 个前端测试全部成功；
- Kong OSS 3.9.3 真实链路按顺序验证：
  - 未携带 API Key：HTTP 401；
  - 有效 API Key、未订阅：HTTP 403；
  - 创建订阅并同步 ACL membership：HTTP 200；
  - 撤回订阅后：HTTP 403；
- 验收退出时已删除临时 Credential、Subscription、Consumer、DataService、模型、数据源、
  Engine 数据源注册和对应 Kong 对象，并恢复本地 Engine 公共地址；
- 真实 Kong 验收发现嵌套插件列表可能不按 `name` 参数过滤。Kong Service 适配器现会在客户端按
  插件名和 Service ID 精确选择对象，并有回归测试覆盖，避免把同一 Service 的 `key-auth`
  误判为 `acl`。
