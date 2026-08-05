# Super API Gateway

Super API Gateway 是一个独立的 Spring Cloud Gateway WebFlux 网关及管理控制台。本目录可以整体迁移到单独仓库，不依赖 DataScalpel 的源码、构建、配置或数据库表。

## 组成

- Server：单端口 `19000`，`/admin-api/v1/**` 提供控制面 API，其他已配置路径承载代理流量。
- UI：独立 Vite 应用，开发端口 `19080`。
- PostgreSQL：与 DataScalpel Admin 可以使用同一个数据库实例和数据库账号，但只使用 `super_api_gateway` schema。
- Kafka：异步输出脱敏访问日志，默认 topic 为 `super-api-gateway.access-log.v1`。

## 本地启动

本工程的功能测试只读取自己的 `config/application-local.yml`，不会读取上级 DataScalpel 配置。

```bash
cp config/application-local.example.yml config/application-local.yml
# 修改数据库、Kafka 和管理凭据
./start-local-dev.sh
```

当前工作区已经建立本地配置，默认地址：

- Gateway 与 Admin API：`http://localhost:19000`
- 管理 UI：`http://localhost:19080`
- 健康检查：`http://localhost:19000/actuator/health`

独立构建与检查：

```bash
./mvnw test
(cd super-api-gateway-ui && pnpm check)
```

Server 和 UI 的构建不需要、也不会触发 DataScalpel 根 Maven Reactor。

## 管理认证

UI 通过 `POST /admin-api/v1/auth/login` 获取管理 JWT。自动化调用使用：

```text
X-Super-Gateway-Admin-Token: <configured-machine-token>
```

除登录、health 和 info 外，管理及 Actuator 接口均需要其中一种凭据。

外部控制系统可以使用不可修改的 `source + externalId` 精确查询其管理资源。API Key
创建和轮换支持调用方提交明文，网关只保存摘要且不回显该明文；API Key 详情仅返回
不可逆摘要用于状态核对。

## 代理访问

- 公开服务直接调用已配置 Route。
- 受保护服务必须提供 `X-API-Key`，并且该 Consumer 已订阅对应 Service。
- Key 不会转发到上游；网关会覆盖注入可信的 `X-Super-Gateway-Consumer-Id` 和 `X-Super-Gateway-Consumer-Code`。

详细设计见 [第一阶段设计](docs/design/phase-one.md)。
