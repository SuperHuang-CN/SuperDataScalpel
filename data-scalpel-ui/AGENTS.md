# DataScalpel 前端开发约定

适用于本目录，继承 [根约定](../AGENTS.md)。本文件保留日常开发边界；页面尺寸、视觉和具体交互统一维护在 [前端页面与交互规范](../docs/development/frontend-ui.md)。修改相应页面前必须阅读该文档对应章节；测试与验证统一遵循根文件政策。

## 结构与技术

- 保持单一 React / TypeScript / Vite 应用，使用 React Router、Ant Design、TanStack Query、AntV X6；不拆微前端或业务 npm 包。新增全局 UI/状态框架须按根约定确认。
- 依赖方向为 `app → modules → shared`：`app` 负责路由、布局、Provider；`modules` 放业务；`shared` 不依赖前两层，只放无业务语义且被至少两个业务域复用的能力。
- 模块按需使用 `api/`、`components/`、`pages/`、`hooks/`、`model/`、`index.ts`。业务代码跟随业务模块，跨模块只通过对方 `index.ts` 公开入口；不为形式解耦添加事件总线、重复 DTO 或适配层。
- 路由在 `app` 装配，页面在模块内实现；业务模块的 `index.ts` 不导出页面，`app` 可直接按页面文件建立懒加载边界。Canvas、Monaco 等大型能力按路由懒加载，不进入普通首页首屏包。页面负责组合，复杂逻辑进入所属 Hook、model 或专用组件。
- 复用 Ant Design 和现有组件，避免简单样式的重复封装、万能表单、空目录或无实际用途的层级。命名表达业务含义，避免 `common`、`misc`、`helper` 收纳目录。
- 使用明确的 TypeScript 类型；JSON 边界使用 `unknown`，不扩散 `any`，不以无理由断言、`@ts-ignore` 或 `eslint-disable` 绕过问题。

## API、查询与状态

- HTTP、拦截和统一错误转换位于 `shared/api`，模块不另建客户端。成功直接消费 DTO / `PageResponse`；错误按 [ProblemDetail 契约](../docs/design/backend-api-response-and-error-handling.md)处理。
- `ApiError` 保留完整 `problem`，包括 `code`、`instance`、`violations`。显示优先使用 `detail`，差异化交互依据 `code`；不各自解析错误响应。错误契约变更同步更新专题文档。
- 通用 Search 类型、操作符、条件、分页和排序构造位于 `shared/search`；模块只定义可查询字段、筛选项和接口，不重复组装逻辑。
- 服务端状态和缓存由 TanStack Query 管理；query key 稳定、可序列化且包含所有影响响应的参数。不长期维护第二份服务端状态。
- 表单用 Ant Design Form，局部交互用 React 状态；跨页面共享状态有实际需求后再设计，不预建全局 Store。

## 自动填充与敏感字段

- 只有登录表单启用自动填充，用户名/密码使用 `username` / `current-password`。自动填充背景覆盖完整输入框容器，图标、文字、可见按钮保持一致，并保留清晰光标和焦点边框。
- 其他生产表单（含筛选、Drawer、Modal、编辑器、Canvas）设置 `autoComplete="off"`。
- 非登录敏感值统一使用 `BusinessSecretInput`，默认掩码并提供显示/隐藏，DOM 用稳定的业务字段 name 和 `autoComplete="off"`。不使用 `Input.Password`、`new-password`、`current-password` 或成对的 `username` / `password` DOM 名称；Form.Item 和 API 字段仍遵循业务契约。
- 禁止隐藏诱饵、随机字段名或聚焦后解除 readonly 等方案。兼容第三方密码管理器前先明确目标产品和取舍，不默认添加厂商私有属性。

## Canvas

- Canvas 属于 `modules/task/canvas`。X6 仅负责图形交互；稳定节点、端口、连线、配置 JSON 不包含 X6 内部对象、Shape、图标、分类、分组或模式能力。
- 新节点在独立目录通过 `CanvasNodeSpec` 接入，声明协议引入版本、分类/二级分组、模式、图规则、默认配置、解析器、安全摘要、元数据引用和动态 Inspector。公共 Shape 保持通用，不扩张 Inspector、IO、端口、摘要等处的节点类型大 switch。
- 元数据按资源种类的 Metadata Provider 扩展；相同资源引用去重且保留全部受影响节点 ID，不在统一 Hook 按节点类型复制查询。公共 Canvas 表单组件至少有两个现有节点真实复用。
- Registry 图规则只提供即时提示；Schema、执行模式、有界性和业务配置以 Task Engine 编译为准。字段候选取 Compiler 的 `inputTables`，不自建 Schema 传播逻辑；当前节点有错时仍展示 Compiler 已安全推导的输入。
- 上游变化使选项失效时保留原值并标记不可用；不静默清空、改名或用前端推测覆盖编译结果。Compiler 暂不可用时显示等待/失败，不能解释为上游无表。
- Inspector 保持紧凑：普通配置、字段错误、失效值和当前执行风险直接展示；低频详情、协议、执行语义、物理路径放在邻近 Tooltip、带 `aria-label` 的图标按钮或 Modal。
- 条件配置按当前模式/选项渲染；重复字段映射和规则用紧凑行或表格，不堆高标题 Card 或大量 Collapse，不隐藏错误和危险操作。
- 修改节点或编译交互前阅读 [Task Engine 规范](../docs/development/task-engine.md#node-operators)及 [编译上下文](../docs/development/task-engine.md#compilation-context)。扩展结构参考 [节点扩展架构](../docs/design/canvas-node-extension-architecture.md)，其中历史迁移步骤不作为新功能约束。

## 页面与交互底线

- 顶部面包屑表达位置。普通列表保持“独立筛选工具带 + 完整结果面板”，名称和总数放结果工具栏，不增加重复大标题、筛选标题或说明区；普通内容边距 12～16px，专业工作区按自身规则。
- 列表和目录占满可用高度并各自内部滚动，表头和分页可见；1920×1080、100% 缩放、菜单和目录展开时无页面或普通表格横向滚动，窄屏降级限于表体。具体宽度和行高遵循 [表格规范](../docs/development/frontend-ui.md#tables)。
- 普通条件只修改草稿，由“查询”或 Enter 提交并回到第一页。目录选择可立即刷新，但只使用已应用条件；更多筛选按实际容器宽度折叠。完整语义见 [查询栏](../docs/development/frontend-ui.md#filters)。
- 普通管理表格使用 small 密度和共享样式；顶级列表分页固定底部，详情嵌入式分页位于顶部结果栏。名称、编码、说明等按语义合并，数值右对齐，空值用 `—`，日期时间统一格式。
- 行操作保持窄列：Hover/键盘聚焦最多两个高频图标加“更多”，触屏可通过“更多”访问全部操作。按钮提供 Tooltip 与对象明确的 `aria-label`；删除须显示具体对象名称并二次确认。
- 普通编辑优先 Drawer；提交操作有 loading、防重复提交和明确反馈。行命令只加载当前行；列表失败持续显示紧凑错误及重试，空数据使用统一 Empty。
- 全前端不使用全宽、大面积、持续占位的 Alert/Banner/色块承载普通说明、警告或局部错误。帮助贴近字段/标题，一两句用 Tooltip，多段用 Popover，入口支持 Hover、键盘 Focus、触屏 Click 和 `aria-label`。
- 复用 `ContextHelp`、`InlineFeedback`、`CompactAlert`。错误存在性和重试/修复操作直接可见；阻断校验、危险确认、数据丢失及整个区域不可用仍在原业务上下文中明确展示，不删除详情或确认信息。
- 视觉遵循现有蓝紫主题和共享组件，通过明确作用域生效；Canvas、Monaco 和专业工作区保持独立边界。调整前阅读 [视觉规范](../docs/development/frontend-ui.md#visual-style)及对应页面章节。
