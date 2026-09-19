# DSH Docker 测试环境

本目录使用官方 npm 发布包 `@deepseek-ai/dsh@0.1.5-rc.1` 构建独立的 DeepSeek Harness（DSH）容器，并通过 DSH 原生 Web 页面进行测试。DSH 核心代码不做修改；监听地址通过 Cordis 配置补丁覆盖。

官方提供 Node.js/Web 启动方式 `npx @deepseek-ai/dsh web`，但截至 2026-09-10，官方主页、快速开始和源码中没有提供官方 Docker 镜像或 Docker 部署指南。本目录是 DataScalpel 工程维护的 Docker 包装。官方资料见 [产品主页](https://www.deepseek.com/harness/en/)和[快速开始](https://deepseek-harness.github.io/deepseek-harness/en/guide/quickstart)。

## 启动和访问

第二阶段独立多用户验证台位于 `http://127.0.0.1:13081`，源码、账号初始化和启动见[验证台 README](../../integrations/dsh-playground/README.md)。它是 Compose 的 `playground` 可选服务，不改变下面默认启动 DSH 原生环境的命令。

在工程根目录执行：

```bash
docker compose -f deploy/dsh/compose.yml build
docker compose -f deploy/dsh/compose.yml up -d
docker compose -f deploy/dsh/compose.yml ps
docker compose -f deploy/dsh/compose.yml logs -f dsh
```

默认访问地址为 <http://127.0.0.1:13080>。DSH 每次启动会生成一次原生访问令牌，并在日志中打印带 `?token=...` 的 URL；首次访问需把日志 URL 中的主机和端口替换为 `127.0.0.1:13080`，浏览器取得会话 Cookie 后会回到不带令牌的根地址。可以在启动前通过 `DSH_WEB_PORT` 修改宿主机端口；容器内部始终监听 `13080`。服务只发布到宿主机回环地址，不应直接作为公网服务使用。

Compose 同时覆盖镜像健康检查，使已有镜像也能检查内部 `13080` 端口；应用本次端口配置可使用 `docker compose -f deploy/dsh/compose.yml up -d --no-build dsh`，无需重新安装镜像内依赖。

查看当前启动令牌：

```bash
docker compose -f deploy/dsh/compose.yml logs --tail=20 dsh
```

首次进入后，在原生页面选择 `/workspace` 作为工作目录。模型凭据在 **Settings → Models** 中配置，写入 DSH_HOME 持久化卷，容器重启后保留。本测试环境不预置模型密钥。

停止服务但保留数据：

```bash
docker compose -f deploy/dsh/compose.yml down
```

只有明确需要删除全部 DSH 配置、会话和工作文件时才使用 `down --volumes`。

## 运行环境

镜像使用固定摘要的 Node.js 24 Bookworm Slim，并固定 DSH、npm 依赖、pnpm 及 Python 包版本。运行时提供：

- `python`、`python3`、`pip`：均指向 `/opt/dsh-python` 中的 Python 3 虚拟环境。
- requests、pandas、openpyxl、PyYAML。
- bash、curl、wget、jq、less、ripgrep、procps、zip、unzip、tar、gzip、ca-certificates、bubblewrap。
- Git、GitHub CLI、完整 C/C++ 构建工具链（build-essential、gcc/cc、g++、libc6-dev、pkg-config）、Python 开发头文件、patch、rsync、SSH、telnet、vim、nano、tree、file。
- pnpm 11.7.0，用于 DSH 原生插件管理。

实际构建版本保存在容器内 `/opt/dsh/runtime-versions.txt` 和 `/opt/dsh/python-versions.txt`。

DSH_HOME 和工作目录分别使用 Compose 数据卷 `datascalpel-dsh_dsh-home`、`datascalpel-dsh_dsh-workspace`。容器通过 `host.docker.internal` 访问宿主机，第三阶段通过独立 Admin Preset 连接系统 MCP；原生页面的 MCP 不被覆盖；用户在原生页面保存的 MCP 配置由持久化卷保留。

工程的 `skills/` 目录只读挂载到容器内 `/workspace/skills`，例如 `/workspace/skills/datascalpel/SKILL.md`。`datascalpel-admin` Preset 已将该目录配置为 Skill 发现根；宿主机编辑后，新一轮 Skill 目录读取即可发现更新，无需构建镜像。更改挂载配置后执行 `docker compose -f deploy/dsh/compose.yml up -d --no-build dsh` 应用，原有配置与工作数据卷保留。

镜像、Compose 和 test69 部署默认使用完全权限 `danger-full-access`（`DSH_PERMISSION_MODE`）。DSH 原生实现将其映射为文件沙箱不限制操作、审批策略 `never`；系统助手的完整工具组合由 `datascalpel-admin` Preset 显式提供，系统 MCP 的开放清单与用户 RBAC 仍独立生效。工具明细见[助手工具清单](../../docs/design/dsh-assistant-tools.md)。

更改默认权限后执行 `docker compose -f deploy/dsh/compose.yml up -d --no-build dsh` 重新创建容器；只执行 `restart` 不会更新环境变量。新会话采用默认权限；原生设置中显式保存的 `permission.defaultPreset` 优先于部署默认值，已有会话的权限事件也会保留，不能把默认值变更视为旧会话的批量迁移。已有原生会话可在页面权限选择器中切换。

保留既有 root、`cap_drop: ALL`、`cap_add: SYS_ADMIN` 和 `no-new-privileges`，以便原生会话仍可切换到 `workspace-write`：发布版 DSH 的 Linux sandbox 通过 bubblewrap 创建 private PID namespace 并挂载 `/proc`，需要 root 与 `CAP_SYS_ADMIN`。当前没有启用 Docker `privileged` 模式；完全权限也不会使只读挂载可写，或让容器访问未挂载的宿主机文件。工程 `skills/` 与部署补丁继续只读挂载。

## 升级

升级前同时修改 `package.json` 中的 DSH 版本和 Compose 镜像标签，重新生成 `package-lock.json`，然后重新构建。DSH 仍处于开发预览阶段，升级后应重新检查 CLI 参数、Cordis 补丁目标、原生页面、插件加载和持久化兼容性。

用户提供的源码归档只用于理解插件、Preset、Skills、MCP 客户端和 Web 监听机制，其版本及摘要记录在 `source.lock.json`，不参与镜像构建。

## DataScalpel Bridge 插件

镜像构建上下文为工程根目录，`Dockerfile.dockerignore` 只允许部署清单和 `integrations/dsh-plugin/` 所需源码进入构建。插件在构建阶段编译打包，安装进 `/opt/dsh/node_modules`，与 DSH 共用运行依赖；依赖版本不一致时构建失败。现有工具链、Python 环境、端口和持久化卷保持原有配置。

默认不启用 Bridge。启用时将本目录 `.env.example` 复制为 `.env.bridge`，设置 `DSH_BRIDGE_ENABLED=true`、已有模型提供方/模型 ID，以及独立随机 `DATASCALPEL_DSH_BRIDGE_TOKEN`。文件不提交，权限设为 `600`，不能填入系统 MCP 令牌或模型 API Key 作为 Bridge 密钥。Compose 从该文件注入环境，不覆盖原生模型密钥存储。

修改源码后，在工程根目录执行：

```bash
docker compose -f deploy/dsh/compose.yml build dsh
docker compose -f deploy/dsh/compose.yml up -d --no-build dsh
```

应用前检查原生任务。修改 `.env.bridge` 后用 `up -d` 重建容器以重新注入环境；单独 `restart` 不会更新容器环境变量。只验证持久化恢复时使用 `restart dsh`，不删除卷。停用 Bridge 时把 `DSH_BRIDGE_ENABLED` 设为 `false` 后执行 `up -d --no-build dsh`，验证工作区和会话记录保留。

内部 HTTP 地址为 `http://127.0.0.1:13080/bridge/v1`；WebSocket 路径为 `/bridge/v1/events?sessionId=...`。全部请求在认证头传入独立 Bridge Bearer，不复用原生页面 Cookie，也不在 URL 中传密钥。仅用于第一阶段单操作者验证，不作为多用户助手入口。

设计与开发说明分别见[路线图](../../docs/design/dsh-integration-roadmap.md)、[第一阶段规格](../../docs/design/dsh-plugin-phase-one.md)和[插件 README](../../integrations/dsh-plugin/README.md)。具体接口及插件维护规则在上述文档维护。

## Admin 接入（第三阶段）

镜像包含 `@datascalpel/dsh-plugin@0.6.0`。默认配置不启用真实用户入口；在不提交的 `.env.admin` 中配置：

```dotenv
DSH_ADMIN_ENABLED=true
DATASCALPEL_DSH_ADMIN_BRIDGE_TOKEN=<独立随机值，至少32字符>
DSH_ADMIN_MCP_URL=http://host.docker.internal:18080/system-mcp
```

同时给 Admin 配置 `data-scalpel.dsh.enabled=true`、`base-url=http://127.0.0.1:13080`、相同 Bridge 密钥和独立 32 字节 Base64 `credential-key`。本机开发仍通过根 `./start-local-dev.sh` 启动，不能将 DSH 的内部 MCP 地址误配成容器 localhost。生产网络地址按部署拓扑分别配置。

文件权限设为 0600；修改环境后用 `docker compose -f deploy/dsh/compose.yml up -d --no-build dsh` 应用。源码更新先 build；不要在容器内修改插件代码。密钥必须随数据库备份保管，丢失后无法解密托管令牌。停用真实接入只需关闭 Admin 和 `DSH_ADMIN_ENABLED`，不删除卷或绑定。前两阶段开关与凭据独立。

详情见[第三阶段设计](../../docs/design/dsh-plugin-phase-three.md)与[验证记录](../../docs/design/dsh-plugin-phase-three-verification.md)。DataScalpel 顶部“AI 助手”已提供会话管理、对话抽屉及[聊天附件](../../docs/design/dsh-chat-attachments.md)；截图需要当前模型支持视觉。插件升级需同步 Admin，保留 DSH_HOME 和工作区卷；控制域兼容读取旧记录，回退旧插件须恢复配套备份以免丢失新增元数据。原生页面及独立验证台继续保留，详见[第四阶段设计](../../docs/design/dsh-plugin-phase-four.md)。

插件升级仍通过本目录 Compose 构建镜像，然后 `docker compose up -d --no-deps dsh`。源码在仓库 `integrations/dsh-plugin/`，启动不下载安装。保留 `dsh-home`、`dsh-workspace` 数据卷及现有环境配置；本次更新不需要新增配置项。归档仅改变插件会话状态，不删除原生历史或工作文件。
