# HTTP API 数据源第一阶段开发计划

## 目标

第一阶段让 DataScalpel 能够把 HTTP/JSON API 作为只读输入数据源使用。系统支持管理可复用的 API 连接、定义具体 API 资源、测试鉴权和资源请求，并在 Canvas 任务中把分页或异步接口返回的数据转换为结构化输入表。

本阶段不把任意脚本、动态上传 JAR、交互式 OAuth、Cookie 会话、NTLM、mTLS、AWS SigV4 或任意多步骤工作流引入管理端。无法由标准配置表达的厂商协议通过随系统发布、可测试和可审计的 Java 连接器实现。

## 能力边界

### API 数据源

- 新增 `HTTP_API` 数据源类型和连接类别，只允许 `SOURCE` 用途。
- 配置 Base URL、非敏感默认 Header、连接/请求超时、限流和重试上限。
- 支持 `NONE`、`BASIC`、`BEARER_TOKEN`、Header/Query `API_KEY`、`OAUTH2_CLIENT_CREDENTIALS` 和通用 `TOKEN_ENDPOINT` 鉴权。
- 密码、Token、API Key、Client Secret、签名密钥和私钥使用独立 AES-GCM 密钥加密保存，接口只返回“已配置”状态。
- 连接测试验证 Base URL 和鉴权流程，不读取业务数据；失败返回脱敏诊断详情并在后台记录脱敏日志。

### API 资源

- API 资源隶属于一个 `HTTP_API` 数据源，包含编码、名称、启停状态和显式输出 Schema。
- 支持 `GET`、`POST` 和 JSON 响应。
- 请求 Path、Query、Header 和 JSON Body 使用受限占位符；只允许 `runtime.*`、`credential.*`、`token`、`timestamp`、`nonce` 和分页/异步运行上下文，不执行 SpEL、JavaScript 或其他脚本。
- 支持 `NONE`、`MD5`、`HMAC_SHA256`、`HMAC_SHA512`、`RSA_SHA256` 签名。签名基于最终序列化请求，并在每页和每次重试前重新计算。
- 支持 `SINGLE_REQUEST`、`PAGINATED_REQUEST`、`ASYNC_JOB` 三种调用模式。
- 分页支持 `PAGE_NUMBER`、`OFFSET_LIMIT`、`CURSOR`、`NEXT_URL`，分页参数可位于 Query 或 JSON Body。
- 异步任务支持提交、提取 Job ID、轮询状态、结果请求及结果分页。
- 资源测试返回 HTTP 状态、Content-Type、耗时、提取记录数和最多 20 行脱敏预览；失败返回脱敏诊断。

### Canvas 任务

- 新增 `HTTP_API_INPUT` 节点，配置 API 数据源、API 资源、输出表名和受控运行时参数。
- 编译阶段使用资源声明的输出 Schema，不发起远程 HTTP 请求。
- 运行阶段执行鉴权、签名、限流、重试、分页或异步轮询，并依据资源 Schema 生成 Spark DataFrame。
- 节点接入统一 `READ` 生命周期、错误分类、诊断 ID 和日志脱敏。
- 运行时设置最大页数、最大行数、最大响应字节数和最大持续时间；检测重复 Cursor/Next URL，Next URL 默认限制为同源地址。

### 连接器扩展

- 管理端和 Task Engine 分别提供稳定的代码连接器注册点；第一阶段内置 `GENERIC_HTTP`。
- 专用连接器跟随应用发布，不支持运行时上传代码。
- 平台统一负责凭据、限制、日志、诊断和输出 Schema；连接器只负责厂商协议的请求推进与响应解码。

## 数据与接口

- `ds_data_source` 继续保存连接聚合；新增 API 非敏感配置大文本和凭据密文大文本，均使用 PostgreSQL `text` 映射。
- 新增 `ds_api_resource`，通过 `data_source_id` UUID 标量关联数据源；请求、签名、调用模式、分页和输出 Schema 保存为类型化 JSON 大文本。
- API 资源提供列表、详情、创建、更新、删除和测试接口。更新、删除、测试继续遵守现有 Action POST 约定。
- 外部 HTTP 调用使用“短事务读取快照 → 事务外远程调用”的结构，不占用管理数据库长事务。

## 开发顺序

1. 增加共享 HTTP API 运行契约、数据源/资源实体、加密凭据和配置校验。
2. 增加数据源与 API 资源 CRUD、连接测试、资源测试和脱敏诊断。
3. 增加 Task Engine 通用 HTTP 执行器、鉴权、签名、分页、异步轮询及连接器注册点。
4. 增加 `HTTP_API_INPUT` 的 Canvas 契约、编译、执行、生命周期日志与错误分类。
5. 增加前端 API 数据源表单、API 资源管理、测试详情以及 Canvas 节点配置。
6. 补充单元/集成测试、本文档和数据源/Canvas 设计文档，执行 `pnpm check` 与 `./mvnw verify`。

## 验收标准

- 可以创建仅用于输入的 HTTP API 数据源，保存后任何查询接口均不返回明文凭据。
- OAuth2 Client Credentials 和通用 Token Endpoint 均在运行时取 Token，并根据有效期缓存；401/403 只允许刷新后重试一次。
- HMAC 和 RSA 签名测试证明时间戳、Nonce、分页和重试会使用最终请求重新签名。
- 四种分页能够正确停止；重复游标、跨源 Next URL、超出页数/行数/字节数/持续时间会产生稳定失败。
- 标准异步接口能够完成提交、轮询、结果读取和结果分页，失败状态与超时可诊断。
- API 资源测试能展示脱敏响应预览和原始技术错误详情。
- Canvas 可使用 `HTTP_API_INPUT` 读取分页 API，并按显式 Schema 向下游传播和执行。
- 新节点遵循统一节点日志、错误分类和结果协议；日志和结果中不出现 Token、密钥、签名或业务数据。
- 前端类型中不新增 `any`，数据源和 Canvas 交互有针对性测试。
- `pnpm check` 和根目录 `./mvnw verify` 通过。
