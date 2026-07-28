# 手动网关状态对账与显式重新同步

## 1. 目标与边界

本阶段为数据服务、API Consumer、API Key 和服务订阅提供单对象手动网关状态对账。DataScalpel 仍是主数据来源，网关是可恢复的执行端。

本阶段明确不包含：

- 定时或事件驱动的自动对账；
- 全量、批量或后台异步对账任务；
- 从网关反向导入未知对象；
- 对账过程中自动修改网关；
- 新增统一的 `resync-gateway` 接口。

一次对象级对账检查该对象现有的全部网关 Binding，每个 Binding 独立保存检查结果。对象没有任何 Binding 时返回 `409 Conflict`，不会为了检查而创建伪绑定。

## 2. 对账与修复职责

对账是只读诊断操作。它只读取网关、比较本地期望并保存诊断结果，不改变原发布、同步、授权或删除状态。

修复必须由用户显式触发现有业务动作：

| 对象 | 对账接口 | 显式修复动作 |
|---|---|---|
| 数据服务 | `POST /api/v1/data-services/{id}/actions/reconcile-gateway` | 期望存在时 `actions/publish`；期望撤回或清理残留时 `actions/unpublish` |
| API Consumer | `POST /api/v1/api-consumers/{id}/actions/reconcile-gateway` | `actions/sync` |
| API Key | `POST /api/v1/api-consumers/{consumerId}/credentials/{credentialId}/actions/reconcile-gateway` | `actions/rotate` |
| 服务订阅 | `POST /api/v1/api-service-subscriptions/{id}/actions/reconcile-gateway` | `actions/sync`；撤回中的订阅继续使用 `actions/revoke` |

API Key 明文从不在 DataScalpel 中持久化。本地只保存 SHA-256 摘要，因此远端密钥丢失或内容不一致时不能恢复原密钥，必须轮换并向调用方交付新密钥。

所有对账接口要求 `service.publish` 权限。

## 3. 持久化状态

四类 Binding 分别增加相同的诊断字段，但不建立统一多态表：

| 字段 | 含义 |
|---|---|
| `reconciliationStatus` | 当前对账状态 |
| `reconciliationReason` | 漂移原因；一致或检查失败时为空 |
| `reconciliationMessage` | 安全、可展示的诊断说明 |
| `reconciliationOperationId` | 当前检查的并发令牌 |
| `reconciliationStartedAt` | 当前检查开始时间 |
| `lastReconciledAt` | 最近一次检查完成时间 |

状态如下：

- `NOT_CHECKED`：当前 Binding 版本尚未手动检查；
- `CHECKING`：检查进行中；
- `IN_SYNC`：远端状态与本地期望一致；
- `DRIFTED`：检查成功，但存在确定的差异；
- `CHECK_FAILED`：网关不可访问、响应异常或读取失败，无法判断是否漂移。

漂移原因如下：

- `REMOTE_MISSING`：本地期望存在，但远端对象不存在；
- `UNEXPECTED_REMOTE`：本地期望不存在，但远端对象仍存在；
- `CONFIG_MISMATCH`：对象存在且归属正确，但配置不同；
- `OWNER_MISMATCH`：名称、外部 ID 或关联对象指向其他归属，禁止接管；
- `LOCAL_BINDING_MISSING`：检查子资源所需的本地 Consumer 或服务 Binding 缺失；
- `SECRET_MISMATCH`：API Key 明文的摘要与本地摘要不同。

发布、取消发布、同步、轮换、授权或撤回动作开始时，旧对账结论失效并恢复为 `NOT_CHECKED`。这些动作本身不会伪装成一次手动对账。

## 4. 事务和并发

外部 HTTP 调用不进入管理数据库事务。一次对账使用三段流程：

1. 短事务锁定业务对象和 Binding，检查没有近期网关操作，为每个 Binding 写入 `CHECKING`、随机 `operationId` 和开始时间，并生成不可变检查快照；
2. 事务外按 Provider 调用只读 `inspect`；
3. 短事务重新锁定 Binding，仅当 `operationId` 仍匹配时写入 `IN_SYNC`、`DRIFTED` 或 `CHECK_FAILED`。

如果检查期间用户执行了新的发布、取消发布、同步、轮换或撤回，新的业务动作会清除旧对账令牌；迟到的检查结果因此被忽略，不会覆盖新状态。

Provider 调用异常记录为 `CHECK_FAILED`，原 `publicationStatus`、`syncStatus`、Credential `status` 和 Subscription `status` 均保持不变。

## 5. Kong OSS 检查规则

### 5.1 数据服务

检查 Kong Service、Route 及访问控制插件：

- Service/Route 的确定性名称、外部 ID、DataScalpel 管理标签和 owner 标签；
- Route 所属 Service、路径、HTTP 方法、协议、`strip_path` 和 `preserve_host`；
- Service 上游 URL；
- 公开服务不应存在 DataScalpel 管理的 `key-auth`/`acl`；
- 订阅访问服务必须存在归属正确且配置匹配的 `key-auth`/`acl`。

Kong OSS 3.9 的 Service 插件列表可能忽略 `name` 查询参数，因此适配器还会在客户端按插件名和 Service ID 过滤，不能直接信任服务端过滤结果。

### 5.2 Consumer

检查 `username`、`custom_id`、外部 ID以及 DataScalpel 管理与 owner 标签。同名对象但 `custom_id` 或标签不匹配时记录 `OWNER_MISMATCH`。

### 5.3 API Key

检查 Credential 所属 Consumer、外部 ID、管理标签和 owner 标签，并对 Kong Admin API 返回的 key 计算 SHA-256 后与本地摘要比较。诊断信息和日志不得包含明文 key。

### 5.4 服务订阅

检查 ACL membership 的所属 Consumer、DataScalpel owner 标签和按数据服务 ID 生成的 ACL group。是否期望远端存在由订阅 `desiredState` 决定，而不是由上一次网关操作状态推断。

## 6. 前端交互

四类列表都同时展示业务操作状态和独立的对账 Tag。Tag 的 Tooltip 展示漂移原因、诊断信息和最近检查时间。

- “立即对账”是独立行级操作，并显示行级 loading；
- 发现漂移后不自动调用修复接口；
- 数据服务期望远端存在时使用“重新发布到网关”修复，期望远端不存在或需要清理残留时使用“取消发布”修复；
- Consumer 继续使用“同步到网关”修复；
- Subscription 根据业务意图使用“同步授权”或“撤回”修复；
- API Key 的 `REMOTE_MISSING` 和 `SECRET_MISMATCH` 明确提示“轮换并重新同步，无法恢复原密钥”。

## 7. 后续演进

自动对账如需引入，应复用当前 Provider `inspect` 和 Binding 状态模型，但必须另行设计调度、分片、限速、失败退避、批次审计和运维告警。本阶段不预留数据库任务表或调度框架。
