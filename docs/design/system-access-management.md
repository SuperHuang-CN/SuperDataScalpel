# 系统访问管理（轻量 RBAC）

## 目标与边界

本系统采用适合内网数据中台的轻量 RBAC：一个用户只属于一个角色，一个角色可配置多个权限。权限用于控制功能与操作，不实现多角色叠加、组织/部门、数据行、字段权限、多租户或审批流。

后端是权限判断的唯一可信来源；前端仅据此隐藏无权菜单和操作入口，不能替代接口鉴权。

## 数据模型

所有实体继承 `BaseEntity`，使用 UUID 与审计时间；实体之间只保存 UUID 标量引用，不建立 JPA 关联或 Lazy 代理。

| 表 | 关键字段 | 说明 |
| --- | --- | --- |
| `sys_user` | `username`、`display_name`、`password_hash`、`role_id`、`enabled` | 系统登录用户；一个用户只有一个角色。用户名不可修改。 |
| `sys_role` | `code`、`name`、`description`、`built_in` | 角色。内置角色不可删除。角色编码不可修改。 |
| `sys_permission` | `code`、`module`、`name`、`description`、`sort_order`、`active` | 稳定的权限目录。权限编码不可修改。 |
| `sys_role_permission` | `role_id`、`permission_id` | 角色与权限的多对多映射，`role_id + permission_id` 唯一。 |

`role_id`、`permission_id` 均为普通 UUID 字段。Service 在创建、修改、删除时显式校验其存在性、有效性与引用关系。

## 权限目录维护方式

权限由 `SystemPermissionDefinition` 在代码中声明，而不是由页面任意创建。应用每次启动都会：

1. 创建缺失的声明权限，并同步名称、模块、说明与排序；
2. 将代码中已移除的权限标记为停用，不物理删除历史记录；
3. 创建或更新内置 `super_admin` 角色，并为其授予全部有效权限；
4. 当配置的初始管理员用户名在数据库中不存在时，创建该用户并赋予 `super_admin`。

因此“权限管理”页面是只读的权限目录，用于审阅系统已有操作能力。新增业务功能时，必须先声明相应权限、在 Resource 上使用 `@PreAuthorize`，再由角色管理页面为自定义角色授权；不提供手工新增、编辑或删除权限的接口。

初始管理员的用户名和密码仍来自 `data-scalpel.security.admin` 配置（可通过 `DATASCALPEL_ADMIN_USERNAME`、`DATASCALPEL_ADMIN_PASSWORD` 覆盖）。该配置只负责首次引导；已创建的用户密码随后由用户管理功能维护，不会因重启被覆盖。

## 当前权限

| 权限编码 | 含义 |
| --- | --- |
| `system.user.view` / `system.user.manage` | 查看 / 管理用户（新增、修改、重置密码、删除） |
| `system.role.view` / `system.role.manage` | 查看 / 管理角色（新增、修改、授权、删除） |
| `system.permission.view` | 查看代码维护的权限目录 |
| `system.configuration.view` / `system.configuration.update` | 查看 / 修改系统配置 |
| `directory.view` / `directory.manage` | 查看 / 管理通用目录 |
| `asset.view` / `asset.manage` | 查看资产登记与同步状态 / 登记、维护、发布、下线、检查和同步资产 |
| `datasource.view`、`datasource.create`、`datasource.update`、`datasource.delete` | 数据源查询及各 CRUD 操作 |
| `datasource.test` | 测试数据源连接 |
| `datasource.metadata` | 读取库表、字段和预览数据 |

后续功能按同样方式增加权限，避免使用笼统的“模块管理员”权限替代具体操作权限。

## API 与认证

认证入口保持公开：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/v1/auth/login` | 用户名密码登录，返回 JWT |
| `GET` | `/api/v1/auth/me` | 返回当前用户名、角色编码与权限编码 |

资产门户的以下只读接口也允许匿名 `GET`，用于展示已发布资产的裁剪后安全元数据：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/asset-portal/overview` | 门户统计、热门标签和顶级业务领域 |
| `GET` | `/api/v1/asset-portal/assets` | 已发布资产分页与服务端筛选 |
| `GET` | `/api/v1/asset-portal/assets/{id}` | 已发布资产公开详情 |

除上述公开查询外，资产管理接口仍需 JWT。`GET /api/v1/assets/{id}/source-navigation` 也必须登录，并根据资产来源类型再次校验对应的 `model.view`、`filedataset.view`、`standard.dictionary.view` 或 `service.view`；浏览器不能用门户身份直接访问原模块管理接口。

AI 助手接口 `/api/v1/assistant/**` 全部要求登录且会话只允许创建者访问。助手不新增 `assistant.use` 权限；工具按照当前 JWT authorities 动态提供并在执行时再次校验。目录查询要求 `directory.view`，目录计划生成和确认执行要求 `directory.manage`。AI 模型管理复用 `system.configuration.view/update`，API Key 只返回是否已配置，不能进入 JWT、接口响应或助手审计。

JWT 的 `roles` 和 `permissions` Claim 由登录时的数据库用户、单一角色及有效权限产生。受保护的 Resource 使用 `@PreAuthorize("hasAuthority('权限编码')")` 进行判断。令牌有效期内的角色调整不会立即影响已签发令牌，重新登录后生效；第一版不维护服务端令牌黑名单。

资源管理接口遵守项目统一 GET/POST 约定：

| 资源 | 查询/创建 | 修改 | 删除 | 其他操作 |
| --- | --- | --- | --- | --- |
| 用户 | `GET`、`POST /api/v1/system/users` | `POST /{id}/actions/update` | `POST /{id}/actions/delete` | `POST /{id}/actions/reset-password` |
| 角色 | `GET`、`POST /api/v1/system/roles` | `POST /{id}/actions/update` | `POST /{id}/actions/delete` | `POST /{id}/actions/update-permissions` |
| 权限 | `GET /api/v1/system/permissions` | 不支持 | 不支持 | 不支持 |

## 关键业务规则

- 用户名和角色编码统一规范化为小写，创建后不允许修改。
- 用户必须绑定存在的角色；不能停用或删除当前登录用户。
- 删除角色前必须确认没有用户引用它；内置角色不能删除。
- 内置 `super_admin` 的权限由启动同步维护，角色管理页面不可修改。
- 配置角色权限时只能选择有效权限；保存时使用映射表全量替换，先删除并刷写旧映射，再写入新映射，保证唯一约束下的更新稳定。
- 所有业务接口默认需要有效 JWT；健康检查、OpenAPI、Swagger、登录入口以及明确列出的资产门户只读接口例外。

## 前端

前端在会话级 `sessionStorage` 保存访问令牌，统一 HTTP 客户端自动附加 `Authorization: Bearer`。启动时调用 `/auth/me` 恢复登录态；令牌无效或过期则清除并返回登录页。资产门户公开请求显式跳过认证头；匿名用户点击“使用资产”时跳转登录并保留当前详情地址。

系统管理下提供紧凑型用户、角色、权限和系统配置页面。菜单、路由和按钮依据 `/auth/me` 的权限编码显示；数据源页还会分别收敛目录、创建、修改、测试、元数据和删除操作。所有实际操作仍由后端鉴权。

## 验证重点

- 启动同步可重复执行，且不会违反角色权限唯一约束。
- 初始管理员可登录，并获得 `super_admin` 与完整权限集合。
- 自定义角色只获得被配置的权限；无权请求返回 `403`。
- 被用户引用的角色不能删除；内置角色不能改授权或删除；当前用户不能自行停用或删除。
- 前端验证登录、菜单收敛、用户/角色 CRUD、角色授权和权限目录只读。
