# Super API Gateway 开发约定

## 独立边界

- 本目录是独立工程，只是暂时与 DataScalpel 同仓放置。不得加入上级 Maven Reactor，不得依赖、导入或运行任何 `data-scalpel-*` 模块。
- DataScalpel 未来只能通过 `/admin-api/v1` HTTP 契约调用网关。不得共享 Java 类型、JPA Entity、前端模块、配置文件或数据库表。
- 所有数据库对象只能位于 PostgreSQL `super_api_gateway` schema；不得查询其他 schema，也不得建立跨 schema 外键。
- 工程必须能够把本目录整体迁移到独立仓库后直接构建和运行。

## 技术基线

- 使用 Java 21、Spring Boot 4.1.0、Spring Cloud 2025.1.2、Spring Cloud Gateway 5.0.2、WebFlux、Netty、Spring Data JPA、Hibernate 和 PostgreSQL。
- Server 强制使用 Reactive Web Application，不得引入 `spring-boot-starter-web`。
- Spring Cloud 兼容校验按已确认方案关闭；升级版本时必须重新开启并验证官方兼容线。
- 第一阶段使用 Hibernate `ddl-auto=update`，数据库 schema 在 Hibernate 启动前创建。
- 管理 UI 使用 React、TypeScript、Vite、Ant Design 和 TanStack Query，不得引用 DataScalpel UI 源码。

## 单应用职责隔离

- Server 是单一进程，但代码必须按 `controlplane`、`dataplane`、`runtime`、`accesslog` 和 `configuration` 分包。
- `/admin-api/v1/**` 是控制面，其他已配置业务路径是数据面；`/admin-api/**` 与 `/actuator/**` 永远不得被代理。
- 控制面可以使用 JPA，但 Repository 与事务必须在专用有界线程池执行。不得在 Reactor Netty EventLoop 上执行 JDBC、JPA、Kafka 阻塞等待或配置加载。
- 数据面请求只读取不可变内存快照，不得查询数据库、调用控制面接口或等待 Kafka。
- Kafka 日志、JPA 管理操作、快照重建、PostgreSQL 通知监听和 Netty EventLoop 必须使用相互独立的线程与容量限制。

## API 与安全

- 管理 API 只允许 GET 和 POST。更新、删除、启停和轮换统一使用 `/actions/{action}`。
- 成功响应直接返回明确 DTO；错误统一返回 RFC 9457 Problem Detail，扩展字段使用 `code`、`timestamp` 和按需的 `violations`。
- 管理 UI 使用独立管理员 JWT；自动化调用使用配置化机器 Token。不得复用 DataScalpel 登录态。
- API Key 明文只允许在创建或轮换响应中出现一次；数据库只保存 SHA-256 摘要、前缀和末四位。
- 访问日志不得记录 API Key、Authorization、Cookie、请求体、响应体、查询参数或原始 Header。

## 本地开发

- Server 默认端口为 `19000`，UI 开发端口为 `19080`。
- 本地配置使用本目录下不提交的 `config/application-local.yml`；不得读取上级 `config/application-local.yml`。
- 使用本目录的 `start-local-dev.sh` 启动，不得修改或依赖 DataScalpel 的根启动脚本。
