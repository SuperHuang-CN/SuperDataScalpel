# 系统配置功能详细设计

## 1. 范围与目标

本功能仅实现系统配置，不实现字典、枚举管理、层级配置或模型字典。

系统配置用于保存由程序明确识别的运行参数。管理员修改后，配置在下一次读取时生效；配置键不是由页面任意创建的，以避免出现“保存了但没有任何程序使用”的无效配置。

第一版的配置项：

| 配置键 | 名称 | 类型 | 默认值 | 当前用途 |
| --- | --- | --- | --- | --- |
| `platform.name` | 平台名称 | `STRING` | `DataScalpel` | 前端左侧品牌和顶部标题 |
| `platform.subtitle` | 平台副标题 | `STRING` | `内网部署 · 模块化单体` | 前端顶部副标题 |

数据库连接、服务端口、JWT 密钥和其他部署密钥继续使用环境变量或外部 YAML，不进入系统配置表和管理页面。

## 2. 后端设计

### 2.1 代码位置

```text
data-scalpel-business/
└── system/configuration/
    ├── domain/        # Entity、值类型、内置配置定义
    ├── repository/    # SearchRepository
    ├── service/       # 查询、更新、校验、初始化
    └── web/           # Resource、请求与响应 DTO
```

该功能属于 `system` 业务域，不放入 `data-scalpel-admin` 或 `data-scalpel-web-core`。

### 2.2 数据模型

表名：`sys_configuration`

| 字段 | Java 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| `id` | `UUID` | 主键 | 继承 `BaseEntity` |
| `config_key` | `String` | 唯一、非空、不可更新 | 程序读取的稳定键 |
| `name` | `String` | 非空 | 页面显示名称 |
| `config_value` | `String` | 非空 | 当前配置值 |
| `value_type` | Enum | 非空 | `STRING`、`INTEGER`、`BOOLEAN` |
| `description` | `String` | 可空 | 使用说明 |
| `sort_order` | `Integer` | 非空 | 页面展示顺序 |
| `created_at`、`updated_at` | `Instant` | 非空 | 继承字段 |

不使用 JSON、`parentId`、逻辑删除、版本号或数据库专属列定义。

### 2.3 业务规则

- `configKey` 由代码中的内置定义声明，创建后不可修改。
- 页面只允许修改 `configValue`，不提供新增或删除操作。
- `STRING` 接受非空文本；`INTEGER` 必须是 Java `Integer`；`BOOLEAN` 仅接受 `true` 或 `false`，保存时规范化为小写。
- 应用启动时仅插入缺失的内置配置，不覆盖数据库中已经被修改的值，不执行删除或全量重置。
- 第一版不缓存配置。业务模块需要读取配置时通过 `SystemConfigurationService` 查询，确保管理页面修改后下一次读取即可得到新值。

### 2.4 API

系统配置接口需要有效 JWT：查询要求 `system.configuration.view`，修改要求 `system.configuration.update`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/v1/system/configurations` | 使用 `SearchRequest` 分页查询配置 |
| `POST` | `/api/v1/system/configurations/{id}/actions/update` | 仅更新配置值 |

查询参数与统一 Search DSL 保持一致：`search`、`page`、`size`、`sort`。前端默认按 `sortOrder,configKey` 排序。

列表成功响应返回项目稳定的 `PageResponse<SystemConfigurationResponse>`，字段为 `content`、`totalElements`、`totalPages`、`page`、`size`；更新成功返回 `SystemConfigurationResponse`。校验失败返回 Problem Details `400`，不存在的配置返回 `404`。

## 3. 前端设计

### 3.1 代码位置

```text
data-scalpel-ui/src/modules/system/
├── api/
├── components/
├── hooks/
├── model/
├── pages/
└── index.ts
```

路由为 `/system/configurations`，在“系统管理”菜单下显示为“系统配置”。

### 3.2 页面与交互

页面采用紧凑型技术管理页布局：当前位置由应用顶部面包屑展示；名称/配置键筛选、刷新操作、配置表格和分页放在同一个容器中。页面不再重复展示大标题和说明区。

配置容器占满页面内容区的剩余高度，表头和分页固定可见，数据行在表体内部滚动，页面主体不产生纵向滚动。

表格展示名称、配置键、当前值、类型、说明和操作。点击“修改”后打开右侧 Drawer：

- `STRING` 使用 Input。
- `INTEGER` 使用 InputNumber。
- `BOOLEAN` 使用 Switch。
- 名称、配置键、类型和说明只读显示。

页面不提供新增、删除或修改配置键的入口。

### 3.3 服务端状态

- 通过 TanStack Query 查询配置列表。
- query key 包含查询条件和分页参数。
- 更新成功后失效 `system-configurations` 前缀下的查询，使列表和应用壳的品牌配置同步刷新。
- 前端通过统一 `SearchRequest` 参数构造工具请求后端；页面筛选在前端转换为稳定的 Search DSL。

应用壳读取配置列表中的 `platform.name` 和 `platform.subtitle`，管理员保存后会自动刷新为新文案。

## 4. 验收与测试

- 后端验证内置配置只在缺失时插入，修改后的值不会被重启覆盖。
- 验证 Search DSL、默认排序和分页经过 `SearchEngine` 执行。
- 验证字符串、整数、布尔值的更新与非法值的 `400` 响应。
- 验证不存在的 UUID 返回 `404`。
- 前端验证查询参数、不同值类型的编辑控件、更新后列表刷新。
- 运行 `./mvnw verify`、`pnpm check`，并用 local Profile 连接 PostgreSQL 做启动和接口冒烟验证。
