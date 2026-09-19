# DataScalpel DSH 插件

对话 Bridge 与独立多用户验证插件，包名 `@datascalpel/dsh-plugin`，当前版本 `0.6.0`。源码在本目录维护，使用 Node.js 24、TypeScript 和 DSH `0.1.5-rc.1` 的公开插件接口。阶段一接口保留；阶段二为固定 Alice/Bob 提供个人工作区、会话、MCP 身份及受控文件工具，使用方式及边界见[独立验证台](../dsh-playground/README.md)。

`0.5.0` 增加聊天附件上传、截图、原生附件持久化和会话范围内的 `attachment_read`。控制域以新增表和可选字段兼容旧版记录。支持范围、消息契约、视觉模型要求及升级说明见[聊天附件](../../docs/design/dsh-chat-attachments.md)。

`0.6.0` 为 Admin 系统助手启用 DSH 完整原生工具组合、持久 Bash、Skills、网页工具、子智能体、目标／计划／工作流、`run_code` 和 Cordis 运行控制。部署默认权限为 `danger-full-access`；工具明细与平台限制见[助手工具清单](../../docs/design/dsh-assistant-tools.md)。

## 开发

在本目录执行 `npm ci`、`npm run build`、`npm test`。锁文件同时固定 DSH 传递依赖，避免预发布版本范围意外升级。`dist/`、`node_modules/` 和打包制品不提交。

HTTP 前缀为 `/bridge/v1`；WebSocket 使用 `/bridge/v1/events?sessionId=...`，两者均在请求头使用独立 Bridge Bearer。验证工作区固定为 `/workspace/bridge-phase-one`。验证 Preset 使用原生上下文压缩，仅提供 `ask_user_question` 工具，不使用已有 MCP 或 Skills。

接口、生命周期与错误契约见[详细规格](../../docs/design/dsh-plugin-phase-one.md)，阶段边界见[路线图](../../docs/design/dsh-integration-roadmap.md)。

## 安装与配置

在工程根目录执行现有 `docker compose -f deploy/dsh/compose.yml build dsh`。构建从仓库源码编译并打包，将发布产物安装进 Host 的同一 `node_modules`，校验所有运行依赖的实际版本；不会复制第二份 DSH/Cordis。启动时无需安装依赖。

将 `deploy/dsh/.env.example` 复制为不提交的 `.env.bridge`，配置独立随机 `DATASCALPEL_DSH_BRIDGE_TOKEN`，再将 `DSH_BRIDGE_ENABLED` 改为 `true`。模型只引用现有提供方和模型 ID，凭据继续由原生提供方读取。应用配置前先检查原生任务状态，再执行 `docker compose -f deploy/dsh/compose.yml up -d dsh`。

不要把密钥放入 URL、命令行参数、会话内容或报告。Compose 的 `.env.bridge` 仅供环境注入，权限应设为 `600`。停用时设 `DSH_BRIDGE_ENABLED=false` 后按同一流程重建容器，保留卷和会话。

Cordis 部署补丁通过 `file:///opt/dsh/node_modules/@datascalpel/dsh-plugin/dist/index.js` 加载镜像中的产物。原生用户 Profile 位于 DSH_HOME，直接使用包名无法向上解析到 `/opt/dsh/node_modules`；显式部署入口避免更改原生 Profile 或复制运行时。

插件版本升级需同步包版本与 Dockerfile 中打包制品名；DSH 升级需核对公开 API、同步 peer/dev dependencies 和 overrides、重新锁定及完成验证。当前版本检查拒绝未适配的 DSH 版本。

镜像工具、容器启动停止和数据保留见[Docker 运维说明](../../deploy/dsh/README.md)。插件卸载只释放自己的路由、连接和 Agent，不清理原生会话或工作文件。

## 运行验收脚本

在本目录使用 Node.js 24 执行：

```bash
node --env-file=../../deploy/dsh/.env.bridge scripts/verify.mjs conversation
```

脚本使用已有模型提供方发出真实基础对话，会产生模型用量；不会调用系统 MCP 或 Skills。脚本验证追问、取消、消息去重、断线和容量后，停在明确的重启检查点。部署操作者在工程根目录执行现有服务的 `docker compose -f deploy/dsh/compose.yml restart dsh`，启动就绪后回到本目录执行：

```bash
node --env-file=../../deploy/dsh/.env.bridge scripts/verify.mjs resume
```

首次创建失败时复用检查点中的客户端 ID；已创建会话后，脚本不会自动重新开始。检查失败会话的状态和结果后，可通过 `conversation --new-run` 明确启动另一轮验证。可选 `DSH_BRIDGE_FOREIGN_SESSION_ID` 指定一个已知原生会话，用于验证归属拒绝；未设置时只检查未知 ID，报告会区分两者。

检查点 `.verification-state.json` 和机器结果 `verification-*.json` 不提交。检查点没有密钥，只保存验证会话、消息 ID、标记和历史边界。报告区分真实模型场景与单元测试；可共享的验证记录维护在 [第一阶段验证记录](../../docs/design/dsh-plugin-phase-one-verification.md)。

重启恢复可能由 DSH 原生持久化补写 `interrupted-tool-result-*`，表示旧工具调用已经中断；这不是重新调用模型或执行工具。Bridge 不恢复旧交互 Promise，也不自动重放遗留 inbox。流式文本不提供跨重启重放，客户端通过状态和完整历史恢复。

## 第三阶段 Admin 接入

新增 `/bridge/v3`，与前两阶段独立认证，源码仍在本目录维护。详见 [第三阶段设计](../../docs/design/dsh-plugin-phase-three.md)。

`verify-admin.py ready` 等待现有 Admin 健康、系统 MCP 目录 READY 和 DSH 就绪；`verify-admin.py setup` 使用现有 Admin 创建或复用专用验证用户，检查并发初始化、托管令牌保护和归属；`model` 验证真实模型与已开放只读 MCP；`resume` 用于重启后的上下文验证。`verify-admin-lifecycle.py` 验证 SSE、追问、取消和专用用户停用/恢复。脚本默认连接 `http://127.0.0.1:18080`，可通过 `DSH_VERIFY_ADMIN_URL` 指定已部署地址；登录凭据使用 `DATASCALPEL_ADMIN_USERNAME/PASSWORD`（本地默认遵循开发脚本）。验证密码与会话 ID 写入不提交的 `.local/dsh-phase-three/verify-state.json`，权限 0600。

运行脚本不会启动应用、不会打开新的系统 MCP API，不要对生产环境运行专用用户生命周期测试。

补充脚本：`verify-admin-boundaries.py` 验证专用角色权限调整、已开放接口临时关闭/恢复、同名用户重建和手动令牌回归；`verify-admin-legacy-jwt.py` 在本机开发配置下验证旧 JWT 兼容与请求上限。必须先确认 Admin 健康且系统 MCP 目录就绪，再运行验证。

## 第四阶段第一批：系统助手 UI

DataScalpel 顶部“AI 助手”打开无模态侧边抽屉，支持会话创建、标题查询、重命名、归档／恢复、Markdown 对话、原生追问及停止执行。插件继续在本目录构建打包，原生 DSH 核心保持不变。

新增能力仅在 v3 提供，v1/v2 保持兼容；归档保留历史和工作文件，不提供永久删除。会话元数据使用兼容旧记录的可选字段，无需数据库迁移。

开发规格与接口：[第四阶段第一批](../../docs/design/dsh-plugin-phase-four.md)。实际环境验证：`python3 scripts/verify-phase-four.py initial`，Admin/DSH 重启后执行 `python3 scripts/verify-phase-four.py resume`。脚本使用已有第三阶段测试账号，不输出凭据。
