# DSH 独立多用户验证台

阶段二源码与手动测试入口。复用一个 DSH 实例，不依赖 DataScalpel 前后端、登录、数据库或系统 MCP。

- 页面：`http://127.0.0.1:13081`。
- 账号：`alice` / `bob`；随机密码在本地 `deploy/dsh/.env.playground`，不提交仓库。
- 普通窗口和无痕窗口分别登录，避免同一浏览器共享 Cookie。
- Node.js 24 内置 HTTP 后端、原生 HTML/CSS/JavaScript 页面；只依赖既有 MCP SDK 和 ws，无额外框架及数据库。
- 浏览器只访问验证台。后端根据 HttpOnly Cookie 确定用户，再选择插件内部路由；Bridge 密钥及 MCP 令牌不会发往浏览器。

## 启动与更新

在工程根目录执行：

```bash
node integrations/dsh-playground/scripts/setup.mjs
docker compose -f deploy/dsh/compose.yml build dsh playground
docker compose -f deploy/dsh/compose.yml up -d --no-build dsh playground
```

前提：按 [DSH 运维说明](../../deploy/dsh/README.md) 建立 `.env.bridge`、启用 Bridge，并在原生 DSH 配置有效模型。复用原生提供方与模型，禁止把模型密钥复制到验证台。

`setup.mjs` 仅首次生成 `.env.playground`，权限 `0600`，重复执行不会修改密码或令牌。该文件及 Bridge 文件均受根 `.gitignore` 保护。环境变量样例见 [配置样例](.env.example)。

```bash
# 状态和验证台日志（DSH 原生启动日志可能含登录链接，分享前自行脱敏）
docker compose -f deploy/dsh/compose.yml ps
docker compose -f deploy/dsh/compose.yml logs --tail=80 playground
# 只停止验证台，保留 DSH 原生环境
docker compose -f deploy/dsh/compose.yml stop playground
# 单独重启，不清除工作区、对话和文件
docker compose -f deploy/dsh/compose.yml restart playground dsh
```

修改 `.env.playground` 后执行 `up -d --no-build playground dsh` 重新创建容器以加载环境；仅 `restart` 不会重新读取 env 文件。停止不删除数据卷，不使用 `down -v`。

后端无持久化数据，重启后需要重新登录。用户会话与文件保存在现有 DSH 持久卷。验证台使用只读容器文件系统、非 root、无额外 capabilities；DSH 沿用已有容器运行权限。测试 MCP 在验证台内部端口 `13082`，不发布到宿主机；DSH 通过 `http://playground:13082/mcp` 连接。

## 手动验证

1. 两个浏览器上下文分别登录 Alice 与 Bob。首次进入自动创建各自工作区。
2. 点击“新建对话”，同时要求模型调用 `whoami`，展开工具记录，检查实际返回的 `authenticatedUser`。
3. 两边创建同名 `note.txt`，写入不同内容；在文件面板及模型工具读取中检查结果。
4. 请求模型实际读取 `../bob/note.txt` 或 `../alice/note.txt`，检查工具拒绝。仅看到模型口头拒绝不能算通过。
5. 要求模型调用 `ask_user_question`；回答选项或自由文本。另一个用户应保持独立执行，也可单独停止。
6. 刷新页面或断网后重连，恢复完整历史与待回答问题。停止只由明确按钮发起。
7. 重启 DSH 后打开自己的会话，点击“恢复会话”，继续之前的上下文。运行中断不会自动重发。

每个用户最多持有四个活跃 Agent；达到上限返回明确错误，可重启 DSH 释放后按需恢复。当前不提供会话删除、账号管理、密码找回、Shell、文件上传下载和生产用户认证。

## 自动验证

```bash
npm ci --prefix integrations/dsh-plugin --ignore-scripts
npm ci --prefix integrations/dsh-playground --ignore-scripts
npm test --prefix integrations/dsh-plugin
npm test --prefix integrations/dsh-playground
# 创建测试会话、真实调用模型（会消耗当前模型额度）
node --env-file=deploy/dsh/.env.playground integrations/dsh-playground/scripts/verify.mjs conversation
# 上一步完成后重启现有 DSH，再验证恢复
docker compose -f deploy/dsh/compose.yml restart dsh
node --env-file=deploy/dsh/.env.playground integrations/dsh-playground/scripts/verify.mjs resume
```

脚本的 `smoke` 模式只验证身份、工作区、会话和边界，不请求模型；它也会创建验证会话。各模式产生的 `.verification-state.json` 仅包含会话 ID 与测试标记，已忽略。

## 隔离范围

工作区是归属组织方式。DSH 原生 `workspace-write` 不限制所有读取，因此第二阶段 Preset 只允许原生追问、作用域内独立 MCP、`workspace_read`、`workspace_write`；不加载原生 Shell、全局文件工具、Skills 或其他 MCP。

文件边界在插件可信代码中强制执行，拒绝绝对路径、父级跳转、符号链接、硬链接及特殊文件，文本上限 128 KiB、目录上限 200 项。它不是任意本地代码、恶意容器 root 或 DSH 原生管理员的内核隔离。持有 Bridge 密钥和能访问 DSH 原生管理面的操作者可信；不得向普通测试用户开放这些入口。

详情与实际验证依据见 [第二阶段设计与验证](../../docs/design/dsh-plugin-phase-two.md)。
