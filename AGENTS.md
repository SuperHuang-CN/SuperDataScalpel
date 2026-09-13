# DataScalpel 工程开发约定

本文件只维护全工程的共同约束。修改前先阅读所属模块的 AGENTS.md，再按 [文档入口](docs/README.md) 阅读本次任务涉及的专题；不要求通读全部设计文档。专题规则在其所属文档维护，历史计划中的版本、迁移步骤和验收要求不自动适用于新任务。

## 工程方向与技术基线

- 面向政府项目和内网部署，采用简单、易维护的模块化单体。业务域允许必要的直接协作；没有当前需求时，不增加模块、分层、接口、工厂、扩展点或微服务拆分设计。
- 默认使用者可信，保留现有轻量单角色 RBAC。新增 ABAC、多租户、数据行权限等细粒度权限体系前，必须先讨论确认。
- 使用 Java 21、Spring Boot 4.1.0、Maven 多模块、Spring MVC、Bean Validation、Spring Data JPA、Hibernate、PostgreSQL；当前使用 `ddl-auto=update`。
- Maven 必须使用根目录 Wrapper 和 `.mvn/maven.config` 指定的 `settings-superhuang.xml`，未经确认不得绕过或替换。
- 新增重要框架、生产依赖、跨模块机制或架构模式前必须讨论确认。当前不引入 jOOQ、Flyway、Spring Modulith、ArchUnit、MapStruct；简单 DTO 映射直接用 Java。
- 前端保持一个 React / TypeScript / Vite 应用，使用 Ant Design、TanStack Query 和 AntV X6。未经确认不引入 Tailwind CSS、shadcn/ui、Redux、其他全局 UI/状态框架或微前端；具体规则见 [前端开发约定](data-scalpel-ui/AGENTS.md)。

## 模块边界

| 模块 | 职责与限制 |
| --- | --- |
| `data-scalpel-contracts` | 跨模块稳定契约；不放运行时基础设施、业务实现或 Spark 类型 |
| `data-scalpel-web-core` | 通用 Web、ProblemDetail、JPA 实体查询；不放领域业务 |
| `data-scalpel-dialect` | JDBC 方言、连接规格、元数据、类型映射与受控数据库操作；不依赖 Spring、JPA 或业务实体，不放业务编排、Controller、任务执行 |
| `data-scalpel-business` | 按 system、datasource、model、task、service 等业务包集中实现；不拆成独立 Maven 模块 |
| `data-scalpel-admin` | Boot 启动与运行配置；新业务 Entity、Repository、Service、Resource 放在 Business |
| `data-scalpel-task-engine` | Canvas 编译、Spark/JDBC Runner；遵循 [Task Engine 开发约定](data-scalpel-task-engine/AGENTS.md) |
| `data-scalpel-task-dispatcher` | 持久化分发及外部 Runner 生命周期监管 |
| `data-scalpel-service-engine` | 数据服务部署与查询运行 |
| `data-scalpel-task-sdk` / `data-scalpel-task-sdk-testkit` | 用户 Spark JAR 的公开 API / 本地测试辅助 API；依赖边界见下文 |
| `data-scalpel-filegdb` / `data-scalpel-shapefile` 及各自 `-s3` | 只读文件解析库与 S3 来源适配器 |

`super-api-gateway` 是同仓独立工程：不加入根 Maven Reactor，不依赖或导入 DataScalpel 的 Java、TypeScript、配置或运行时基础设施。双方仅通过公开 HTTP 契约集成；网关只使用 PostgreSQL `super_api_gateway` schema，不访问其他 schema 或建立跨 schema 外键。它维护自己的 Wrapper、依赖、前端、配置、启动脚本、文档和发布周期；修改前阅读其 [AGENTS.md](super-api-gateway/AGENTS.md)。

## 实体与事务

- 业务实体继承 Business 的 `BaseEntity`，使用 `UUID`、`createdAt`、`updatedAt`；主键用 `GenerationType.UUID`，不以专属列定义、注解或转换器绑定数据库 UUID 类型。
- 实体间默认保存 UUID 标量引用；Service 明确校验存在性、类型匹配、删除保护和生命周期。不冗余关联对象的易变名称、路径等字段。
- JPA Entity 关联仅在强聚合且级联能明确降低复杂度时考虑；引入前评估查询、级联、序列化和修改边界并确认。Lazy 关联不是默认方案。
- 默认不使用逻辑删除或乐观锁；停用、归档使用具体业务状态。API 不直接暴露 Entity。
- 大文本 String 禁用 `@Lob`；使用 `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)`，在 PostgreSQL 中保存为 text。
- 外部 JDBC/HTTP 操作采用“短事务读取快照 → 事务外执行 → 短事务提交状态并组装响应”，不得置于管理数据库长事务。

## Web 与查询契约

- Web 按 `web/resource`、`web/request`、`web/response` 分包。Resource 仅负责路由、参数绑定、Validation、状态码和调用 Service；不放业务规则或 JPA 查询。
- Request 只表达输入及校验，Response 只表达输出，均不定义为 Resource 内部类。Service 可直接接收 Request、返回 Response；仅稳定且实际跨模块复用的 DTO 才进入 Contracts。
- 业务接口只用 GET / POST：只读用 GET，创建用集合地址 POST；更新、删除及其他命令用 `POST /api/v1/{resources}/{id}/actions/{action}`，集合命令省略 `{id}`。更新/删除的 action 固定为 `update` / `delete`。
- 仅复杂只读条件无法合理编码到 URL 时允许 `POST .../actions/query-*`，Resource、DTO、文档均须标明只读且不得改变状态。Action 使用明确业务 DTO；调整旧接口时迁移到上述形式。
- 成功直接返回 Response DTO、`PageResponse`、文件流或空响应，并使用准确状态码；不增加 `Result<T>` / `ApiResponse<T>` 包裹层。
- HTTP 错误统一 RFC 9457 `ProblemDetail` / `application/problem+json`：标准字段加稳定 `code`、`timestamp`，校验失败加 `violations`。使用 Web Core 的 Factory、异常映射和安全响应，禁止手工错误 JSON、`sendError` 或并行错误协议。
- 预期业务错误可用准确状态码的 `ResponseStatusException`；未知异常记录完整日志，对外只返回安全的通用 500。修改错误映射、认证拒绝或错误契约时同步 [错误处理规范](docs/design/backend-api-response-and-error-handling.md)。
- 新增或修改 REST 接口必须同步维护 OpenAPI 中文契约：Resource 分类、接口标题与行为、路径/查询参数、请求/响应及枚举的所有对外字段均须有准确说明，并在适用时写明单位、默认值、空值、范围、前置条件、副作用、状态变化和主要失败语义；详见 [OpenAPI 契约](docs/design/backend-api-response-and-error-handling.md#openapi-契约)。
- Business/Admin 中使用 Swagger `@Tag` / `@Operation` / `@Parameter` / `@Schema`；Contracts 保持无 Swagger 依赖，使用 Jackson `@JsonClassDescription` / `@JsonPropertyDescription` 并由 Admin 的 OpenAPI 配置转换。以运行时 `/v3/api-docs` 为最终验收结果；源码有注解但 `$ref`、`allOf` 或多态转换后说明丢失，仍视为契约不完整。
- 普通实体列表统一 `SearchRequest`、`SearchEngine`、`SearchRepository`；固定业务条件用 `Specification` 重载，始终与客户端条件 AND。不重复实现解析、转换、分页、排序或字段校验。
- [README Search DSL](README.md#实体通用查询) 是稳定契约，改变语法或语义前须讨论。仅支持实体及父类的标量字段；不支持关联、集合、JSON、LOB 或嵌套路径。报表、聚合和动态物理表查询使用对应业务能力。

## 平台类型与任务契约

- 模型、任务、数据服务统一使用 Contracts 的 `PlatformDataType` / `PlatformTypeDefinition`。物理类型与平台类型双向转换仅由 Dialect 实现；JDBC 类型停留在元数据边界，`LOSSY` / `UNSUPPORTED` 必须阻止导入或建表。
- Java Canvas 定义唯一来源是 Contracts；Business、Engine、Manifest 不复制节点、配置、表达式、枚举或整棵转换树。草稿资源 ID 用字符串表达空值，UUID 在保存/发布/编译校验边界解析并转换为稳定问题。
- 新增 Canvas 节点须声明协议引入版本、模式、分类、图度数、Schema/有界性传播与安全摘要，并使用显式编译期注册表；没有运行时第三方节点需求时，不引入反射、Spring 扫描、ServiceLoader、远程模块等动态节点插件机制。
- Spark 类型只在执行侧与平台类型显式转换，不进入实体、REST 或核心模块。前端只保存与 X6 解耦的稳定定义，并使用 Compiler 提供的 Schema 上下文。
- SDK 是用户 Spark JAR 唯一公开兼容面，只依赖 Spark 公共 API；不依赖 Spring、JPA、Canvas、Manifest 或 Engine 内部实现。用户以 `provided` 引入 SDK/Spark，自带其他依赖，不把 Engine Uber JAR 作为编译依赖。
- TestKit 仅依赖公开 SDK/Spark 公共 API，不依赖控制面、Contracts、Engine、Spring、JPA、Testcontainers；用户以 `test` 引入，不打入运行 JAR。模拟范围与真实外部系统边界见 [SDK 规范](docs/development/task-engine.md#spark-jar)。
- 修改 Canvas、Runner、Dispatcher 执行契约或 SDK 时，先读 [Task Engine 开发约定](data-scalpel-task-engine/AGENTS.md)及其对应专题。资源绑定用于授权声明与凭据最小化，不构成 JVM 沙箱。
- 除任务明确要求外保持现有 API 行为兼容；对外配置、协议或 API 行为变化时同步所属专题文档。

## 本地开发与调试

- DataScalpel 的功能自测、页面/接口验证和联调统一使用用户的开发环境，包括代理主动决定进行的验证。需要启动应用时，必须从工程根目录执行 `./start-local-dev.sh`；除非用户明确要求隔离环境，不得绕过脚本另起临时前端、后端、数据库或 Mock 应用环境。
- 启动前先检查现有进程归属、健康状态及是否已加载本次修改，可复用时直接复用，需要重启时仍通过脚本启动。端口冲突先定位原因，不得自行换端口启动第二套实例或停止无关进程。
- 沿用脚本管理的端口、依赖和配置：Spring Boot 应用使用 `local` Profile 与根目录不提交的 `config/application-local.yml`，非 Spring Boot 的 Task Engine 使用脚本指定配置。不得为通过自测临时更换 Profile、数据库、Schema 或外部服务地址。
- 启动失败时查看脚本终端输出和健康检查结果，在任务范围内排查脚本、配置或依赖问题；不得悄悄改用独立 `pnpm dev`、Vite、`spring-boot:run`、`java -jar` 等启动方式。`--prepare` 仅用于编译与准备，不能替代启动和功能验证。
- 保留启动终端或会话，默认在调试结束后保留开发环境供用户继续使用，不按一次性测试环境自动清理。操作细节见 [本地启动说明](README.md#本地开发与调试)。本节约束启动方式，不恢复强制测试要求。

## 测试与验证（暂时禁用）

- 当前不强制补充或执行测试、构建、联调，也不以未验证或未报告验证边界阻止交付。此政策统一覆盖后端模块测试、根 `verify`、公共契约针对性测试、前端依赖变更后的 `pnpm check`、Canvas 单元/端到端测试及外部服务验证说明；用户在具体任务中明确要求的验证仍须完成。子规范和历史计划中的测试清单不自动恢复这些要求。
- Maven 隔离自动化测试继续使用各模块 `application-test.yml`，不因本地调试约定而连接共享环境；需要运行完整系统的功能验证遵循上节统一启动约定。
