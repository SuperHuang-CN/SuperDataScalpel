# DataScalpel 工程开发约定

## 项目方向

- DataScalpel 是面向政府项目、以内网部署为主的后端系统。架构上优先选择简单、易维护的模块化单体；除非用户明确提出，否则不为未来拆分微服务做预先设计。
- 默认使用者可信。保持现有认证机制轻量；除非用户明确提出，否则不引入 RBAC、ABAC、多租户、数据行权限等细粒度权限体系。
- 避免没有当前需求支撑的抽象和基础设施。新增重要框架、生产依赖、跨模块机制或架构模式前，必须先与用户讨论确认。

## 技术基线

- 使用 Java 21、Spring Boot 4.1.0、Maven 多模块、Spring MVC、Bean Validation、Spring Data JPA、Hibernate 和 PostgreSQL。
- 第一版本使用 Hibernate `ddl-auto=update` 管理数据库结构变化。
- 第一版本不引入 jOOQ、Flyway、Spring Modulith 或 ArchUnit，除非用户重新确认这一决策。
- Maven 必须使用工程内的 Wrapper。`.mvn/maven.config` 已指定根目录的 `settings-superhuang.xml`；未经确认，不得绕过或替换该 Maven 配置。

## 前端约定

- 前端位于根目录 `data-scalpel-ui`，是独立的 Vite 工程，不拆分为微前端或多个前端应用。
- 前端与后端采用一致的模块化单体思路：保持一个应用，按照系统管理、数据源管理、模型管理、任务管理、数据服务等业务域组织代码；业务域之间允许真实、必要的协作，不为形式上的完全解耦增加事件总线或中间层。
- 使用 React、TypeScript、Vite、Ant Design、TanStack Query 和 AntV X6。Ant Design 是唯一的全局 UI 组件体系；未经确认，不引入 Tailwind CSS、shadcn/ui、Redux 或其他全局状态/UI 框架。
- 普通页面优先使用 Ant Design 的 Table、Form、Drawer、Tree、Select 等组件；服务端数据由 TanStack Query 管理，局部交互状态优先使用 React 自身状态。
- 可视化任务编排统一使用 AntV X6。后端持久化的是与 X6 解耦的节点/连线 JSON 契约，不得直接持久化 X6 内部对象。
- X6 节点配置必须使用明确的 TypeScript 类型；不得在新代码中扩散 `any`。
- 前端新增或修改依赖后，运行 `pnpm check`；Canvas 交互变更还应补充相应的单元或端到端测试。
- 前端目录、依赖方向、模块公开入口和统一查询等具体规范，以 `data-scalpel-ui/AGENTS.md` 为准。

## 模块职责

- `data-scalpel-contracts`：存放模块间共享且稳定的请求、响应等契约。不得放入运行时基础设施和具体业务实现。
- `data-scalpel-web-core`：存放通用 Web 基础设施、错误处理和 JPA 实体查询能力。不得放入领域业务逻辑。
- `data-scalpel-dialect`：存放不依赖 Spring、JPA 和业务实体的 JDBC 方言、连接规格、元数据模型与只读数据库检查能力。不得放入业务编排、Controller、数据源实体或任务执行逻辑。
- `data-scalpel-business`：统一存放业务实现，按照 `system`、`datasource`、`model`、`task`、`service` 等业务包组织；这些业务包允许真实、必要的直接协作，不拆成独立 Maven 模块。
- `data-scalpel-admin`：Spring Boot 可执行应用和运行配置。不得把新的业务实体、Repository、Service 或 Controller 放入该模块。
- 没有明确需求时，不新增模块，也不随意调整现有模块职责。

## 实体约定

- 业务实体继承 `data-scalpel-business` 中的 `BaseEntity`，统一使用 Java `UUID` 主键以及 `createdAt`、`updatedAt` 字段。
- UUID 使用 JPA 标准 `GenerationType.UUID` 生成；不得通过 `columnDefinition`、PostgreSQL 专属注解或转换器绑定具体数据库类型。
- 业务实体之间默认只保存 `UUID` 标量引用，例如 `directoryId`、`datasourceId`、`modelId`；尽量避免 `@ManyToOne`、`@OneToOne`、`@OneToMany`、`@ManyToMany`、Lazy 代理和双向 Entity 关联。
- 标量 UUID 关联的存在性、业务类型匹配、删除引用保护和生命周期一致性由 Service 明确校验；不得为了减少一次查询而在业务实体中冗余关联对象的名称、路径或其他易变化字段。
- 只有当实体确实属于同一个强聚合、需要由 JPA 级联维护共同生命周期，并且 Entity 关联能够明确降低整体复杂度时，才可以考虑使用 JPA Entity 关联。引入前必须先评估查询行为、级联范围、序列化风险和修改边界，向用户说明必要性与取舍并确认；不得把 Lazy 关联作为默认建模方式。
- 默认不使用逻辑删除和乐观锁；停用、归档等含义使用具体业务状态表达。
- API 使用明确的请求和响应 DTO，不直接向前端暴露 JPA Entity。
- 第一版本不引入 MapStruct；简单 DTO 映射使用直接、明确的 Java 代码。

## 大文本与事务约定

- 新增大文本 `String` 字段不得使用 `@Lob`。优先使用 `@JdbcTypeCode(SqlTypes.LONG32VARCHAR)`，在 PostgreSQL 中保存为 `text`，避免 Large Object/OID 事务限制。
- 外部 JDBC、HTTP 调用不得放进管理数据库长事务。使用：短事务读取快照 → 事务外执行外部操作 → 短事务提交状态并组装响应。

## 后端 REST API 约定

- 对外业务接口只允许使用 HTTP `GET` 和 `POST`；新代码不得声明 `PUT`、`PATCH` 或 `DELETE` 接口。
- 查询详情、列表和其他只读操作使用 `GET`；创建资源使用集合地址上的 `POST`，例如 `POST /api/v1/data-sources`。仅当只读查询必须提交复杂的结构化条件、无法合理编码为 URL 参数时，允许使用带 `actions/query-*` 的 `POST`；该接口必须在 Resource、DTO 和文档中明确“只读”，且不得改变资源状态。
- 更新资源统一使用 `POST /api/v1/{resources}/{id}/actions/update`，不得使用 `PUT` 或 `PATCH`。
- 删除资源统一使用 `POST /api/v1/{resources}/{id}/actions/delete`，不得使用 HTTP `DELETE`。
- 测试、移动、发布、启停等其他命令也统一使用 `POST /api/v1/{resources}/{id}/actions/{action}`；不需要资源 ID 的集合级命令使用 `POST /api/v1/{resources}/actions/{action}`。
- Action 接口必须使用表达业务含义的明确请求和响应 DTO。现有不符合本约定的接口也应在对应功能调整时完成迁移，不得继续扩散旧形式。

## 后端响应与异常处理

- 成功响应直接返回明确的 Response DTO、`PageResponse`、文件流或空响应；不得新增 `Result<T>`、`ApiResponse<T>` 等成功响应包裹层。创建、删除等操作应使用准确的 HTTP 状态码。
- 所有 HTTP 错误响应统一使用 RFC 9457 `ProblemDetail` 和 `application/problem+json`。标准字段为 `type`、`title`、`status`、`detail`、`instance`，扩展字段固定使用稳定的 `code`、`timestamp`，字段校验失败时使用 `violations`；不得另造 `path`、`success`、`message` 等并行错误协议。
- `ProblemDetailFactory`、全局异常映射和安全响应位于 `data-scalpel-web-core`。业务 Resource、Service、安全配置与 Service Engine 不得手工拼装错误 JSON 或使用 `sendError` 绕过该机制。
- 现有 `ResponseStatusException` 仍是可用的直接业务错误表达方式；新增业务错误应选择准确的 HTTP 状态，公共处理器会映射为稳定问题码。未预期异常必须记录完整日志，但对外只返回安全的通用 500 信息。
- 修改错误映射、认证/鉴权失败行为或错误契约时，必须补充相应测试，并同步更新 [后端 API 响应与异常处理](docs/design/backend-api-response-and-error-handling.md)。

## 后端 Web 代码结构

- 每个业务域的 Web 代码按 `web/resource`、`web/request`、`web/response` 分包：`resource` 仅存放 REST Resource，`request` 仅存放客户端输入 DTO，`response` 仅存放对外输出 DTO。不得将三类对象直接混放在 `web` 包下。
- `Resource` 负责路由、参数绑定、Bean Validation、HTTP 状态码和调用 Service；不得承载领域业务规则或 JPA 查询细节。
- `Request` 只表达接口输入与校验约束；`Response` 只表达接口输出。对外 DTO 不定义为 `Resource` 的内部类，也不得用 JPA Entity 直接替代 DTO。
- 第一版本 Service 可以直接接收 Request、返回 Response，以保持实现直观；没有明确收益时，不额外增加 Command、Assembler、Mapper 等转译层。
- 仅当请求或响应契约确实需要跨模块复用且已经稳定时，才移入 `data-scalpel-contracts`；普通业务域 DTO 留在所属业务域的 `web` 包内。

## 实体查询约定

- 普通业务实体的列表查询统一使用 `SearchRequest`、`SearchEngine`，对应 Repository 统一继承 `SearchRepository`。
- 查询包含 `projectId`、`catalogId` 等强制业务条件时，使用 `SearchEngine` 接收固定 `Specification` 的重载方法；固定条件必须始终通过 `AND` 与客户端查询条件组合。
- 业务模块不得重复实现动态条件解析、分页、排序、类型转换或字段合法性校验。
- `README.md` 中记录的 Search DSL 是稳定的对外契约。修改语法或语义前必须先讨论，确认后补充相应测试。
- 第一版本只支持实体自身或父类声明的标量字段，不支持关联对象、集合、JSON、LOB，以及 `a.b` 形式的嵌套路径。
- 复杂报表、聚合统计和动态物理表查询不属于实体查询能力，不得为了这些场景扭曲当前查询 API。

## 实现原则

- 模型、任务和数据服务的稳定标量类型统一使用 `data-scalpel-contracts` 中的 `PlatformDataType` 和 `PlatformTypeDefinition`。不得在业务模块或前端重新定义数据库原生类型到平台类型的映射。
- 物理类型到平台类型、平台类型到物理类型的双向转换只能由 `data-scalpel-dialect` 实现；JDBC 类型只允许停留在元数据边界。映射存在 `LOSSY` 或 `UNSUPPORTED` 时必须阻止导入或建表，不得静默截断精度、长度、值域或时区语义。
- Spark 类不得进入 JPA 实体、REST 契约或核心模块。实际接入 Spark 时，由执行模块使用 Java `DataTypes` 与 `PlatformTypeDefinition` 显式转换。

- 优先使用直接、清晰的 Spring/JPA 实现和职责集中的小类。没有当前使用场景时，不增加额外分层、接口、工厂或扩展点。
- 除非任务明确要求修改，否则保持现有 API 行为兼容。
- 修改查询解析、类型转换、分页、排序、异常映射、认证行为或公共契约时，必须增加或更新有针对性的测试。
- 对外配置和 API 行为发生变化时，同步更新相关文档。

## 验证要求

- 开发过程中可以使用 `./mvnw -pl <module> -am test` 进行模块级验证。
- 后端代码修改完成前，必须在工程根目录运行完整构建：`./mvnw verify`。
- 如果因为外部服务不可用而无法完成验证，必须明确说明已经验证和未验证的内容。
