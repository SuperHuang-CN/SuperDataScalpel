# linux69 test69 AI 助手部署交接

## 给服务器 Codex 的任务

你当前运行在 linux69（固定 IP `10.0.0.69`）上。请继续完成 DataScalpel
test69 环境的 DSH AI 助手部署、应用启动和验证。直接执行必要操作并持续排查，
不要只给方案。除非 Portainer 登录或模型 API Key 确实需要用户提供，否则不要中途
停下来询问。

开始前阅读仓库根目录 `AGENTS.md`、`deploy/test69/README.md` 和
`deploy/dsh/README.md`。所有命令从 `/data/SuperDataScalpel` 执行。

## 工程和环境背景

DataScalpel 是 Java 21、Spring Boot、Maven 多模块和 React/Vite 工程。
test69 是 linux69 上的独立测试环境：

- Admin、UI、Service Engine、Task Engine、Task Dispatcher 由
  `start-local-test69.sh` 统一启动。
- PostgreSQL 和 MinIO 位于 linux5（`10.0.0.5`），test69 使用独立数据库、
  角色、bucket 和最小权限账号。
- Kafka 4.3.1 和 Kong 3.9.3 位于 linux69，由 Portainer Stack
  `datascalpel-test69-infra` 管理。
- Compute Engine 容器名为 `datascalpel-compute-engine`，必须保留。
- 本次新增 DSH AI 助手，必须由独立 Portainer Stack
  `datascalpel-test69-dsh` 管理。
- DSH Admin Bridge 由宿主机 Admin 访问容器端口 `13080`；DSH 容器通过
  `host.docker.internal:18080/system-mcp` 回连 Admin。

应用访问地址：

| 组件 | 地址 |
| --- | --- |
| DataScalpel UI | `http://10.0.0.69:18887` |
| Admin | `http://10.0.0.69:18080` |
| Service Engine | `http://10.0.0.69:8081` |
| DSH 原生页面 | `http://10.0.0.69:13080` |
| Portainer | `http://10.0.0.69:9000` |

test69 管理员用户名是 `test69admin`。密码和所有运行密钥只保存在
`/data/datascalpel-test69/config/runtime.env`，禁止打印、提交或写入日志。

## 已完成状态

截至交接时，GitHub `main` 的目标提交是 `ed4816c6`，包含：

- `deploy/test69/dsh-compose.yaml`
- `start-local-test69.sh` 的 DSH 接入和启动前检查
- `runtime.env.example` 的 DSH 密钥声明
- DSH Dockerfile 对独立编译基础镜像的支持
- DSH 插件构建使用 8 GiB Node 堆上限
- Bridge Token 使用宿主机权限为 `600` 的只读文件挂载，不进入 Compose 或
  Portainer 环境变量

linux69 上此前已经完成：

- 构建 `datascalpel-dsh:0.1.5-rc.1` 成功。
- 存在本地辅助镜像 `datascalpel-node:24-bookworm-slim` 和
  `datascalpel-node-build:24-bookworm`。
- `datascalpel-node-build:24-bookworm` 使用 npm `11.16.0`。旧 npm `11.6.0`
  在安装仅 161 个插件依赖时会异常耗尽 8 GiB 堆，不要降级。
- `runtime.env` 已幂等加入 `DATASCALPEL_DSH_BRIDGE_KEY` 和
  `DATASCALPEL_DSH_CREDENTIAL_KEY`。
- `/data/datascalpel-test69/config/dsh-admin-bridge-token` 已从 Bridge Key 生成，
  权限为 `600`。
- `/data/datascalpel-test69/dsh/home` 和 `workspace` 已创建，权限为 `700`。
- `docker compose -f deploy/test69/dsh-compose.yaml config --quiet` 已通过。

尚未完成：

- Portainer Stack `datascalpel-test69-dsh` 尚未创建。此前浏览器登录会话因
  linux69 重启失效。
- linux69 重启后，五个由脚本托管的应用没有自动恢复，需要重新启动。
- DSH 尚未配置真实模型。用户还没有提供模型 API Key，禁止凭空设置。
- 尚未完成 DSH、Admin Bridge 和 UI AI 助手的最终验证。

## 安全和操作约束

- 不得打印或提交 `runtime.env`、密码、JWT、Bridge Token、DSH 原生访问 Token
  或模型 API Key。
- 分享 DSH 日志前必须把 `token=` 后的值替换为 `<redacted>`。
- 只修改 Portainer Stack 管理的容器；不要修改或删除其他环境的容器、卷、网络
  和数据库。
- 保留 `datascalpel-compute-engine` 和 `datascalpel-test69-infra`。
- 不得用普通 `docker compose up` 代替 Portainer Stack。缺少 Portainer 认证时，
  让用户在 `http://10.0.0.69:9000` 登录一次，然后继续。
- 不要把 DSH Bridge Token 粘贴到 Portainer。Stack 使用宿主机密钥文件挂载。
- 项目只能通过 `./start-local-test69.sh` 启动，不得分别运行 Maven、Vite 或 JAR。
- Maven 只能使用仓库 Wrapper 和 `settings-superhuang.xml`。

## 执行步骤

### 1. 核对代码、镜像和中间件

```bash
cd /data/SuperDataScalpel
git pull --ff-only
git rev-parse --short HEAD
git status --short

docker image inspect datascalpel-dsh:0.1.5-rc.1 >/dev/null
docker image inspect datascalpel-node:24-bookworm-slim >/dev/null
docker image inspect datascalpel-node-build:24-bookworm >/dev/null

docker ps --format '{{.Names}} {{.Status}}' \
  | grep -E 'datascalpel-test69-(kafka|kong)|datascalpel-compute-engine|superportainer'
```

如果只有最终 DSH 镜像缺失、两个辅助镜像仍在，可按当前仓库重新构建：

```bash
docker build \
  --network host \
  --pull=false \
  --build-arg NODE_IMAGE=datascalpel-node:24-bookworm-slim \
  --build-arg NODE_BUILD_IMAGE=datascalpel-node-build:24-bookworm \
  --build-arg NODE_BUILD_TOOLS_PREINSTALLED=true \
  -f deploy/dsh/Dockerfile \
  -t datascalpel-dsh:0.1.5-rc.1 \
  .
```

linux69 Docker daemon 配置的旧代理 `http://10.0.0.56:7892` 曾导致 Docker Hub
拉取失败；不要为了本任务改 daemon 全局配置。构建必须优先复用本地镜像并使用
`--network host`。如果辅助镜像也不存在，先核对是否发生过镜像清理，再决定恢复
路径，不能删除其他镜像腾空间。

目标提交应至少包含 `ed4816c6`。如果仓库已经有更新提交，确认上述 DSH 文件仍在
再继续，不要回退代码。

Kafka、Kong、Portainer 和 Compute Engine 应为运行状态。不要因为非关键容器的
状态而重建或删除现有环境。

### 2. 幂等准备私密文件和目录

以下操作不得启用 shell xtrace：

```bash
set +x
umask 077

runtime_env=/data/datascalpel-test69/config/runtime.env
test "$(stat -c '%a' "$runtime_env")" = 600

source "$runtime_env"
test -n "$DATASCALPEL_DSH_BRIDGE_KEY"
test -n "$DATASCALPEL_DSH_CREDENTIAL_KEY"

printf '%s' "$DATASCALPEL_DSH_BRIDGE_KEY" \
  > /data/datascalpel-test69/config/dsh-admin-bridge-token
chmod 600 /data/datascalpel-test69/config/dsh-admin-bridge-token
unset DATASCALPEL_DSH_BRIDGE_KEY DATASCALPEL_DSH_CREDENTIAL_KEY

install -d -m 700 \
  /data/datascalpel-test69/dsh/home \
  /data/datascalpel-test69/dsh/workspace

cd /data/SuperDataScalpel
docker compose -f deploy/test69/dsh-compose.yaml config --quiet
```

### 3. 创建 Portainer 受管 Stack

在 Portainer 的 local 环境中创建 Stack：

- 名称：`datascalpel-test69-dsh`
- Compose：`/data/SuperDataScalpel/deploy/test69/dsh-compose.yaml`
- 不设置 Stack 环境变量。
- 不修改已有 `datascalpel-test69-infra` Stack。

可使用 Portainer HTTPS/HTTP API 或已登录 Web UI。使用 Web UI 时进入
**Stacks → Add stack → Web editor**，粘贴以下命令输出的无敏感 Compose：

```bash
cat /data/SuperDataScalpel/deploy/test69/dsh-compose.yaml
```

部署完成后验证：

```bash
docker ps --filter name=datascalpel-test69-dsh
docker inspect \
  --format '{{.State.Status}} / {{if .State.Health}}{{.State.Health.Status}}{{end}}' \
  datascalpel-test69-dsh
```

等待结果为 `running / healthy`。如果失败，查看经过脱敏的日志：

```bash
docker logs --tail 200 datascalpel-test69-dsh 2>&1 \
  | sed -E 's/(token=)[^&[:space:]]+/\1<redacted>/g'
```

重点检查：密钥文件挂载、`SYS_ADMIN` capability、`host.docker.internal`、
端口 `10.0.0.69:13080` 和 DSH Cordis patch。

### 4. 准备并启动五个应用

先确认端口没有残留进程：

```bash
ss -ltnp | grep -E ':(8081|18080|18091|18092|18887)[[:space:]]' || true
pgrep -af 'start-local-test69|data-scalpel-admin|data-scalpel-service-engine' || true
```

如果端口已经由当前 test69 脚本实例占用，先判断是否健康且是否加载最新代码，
不要启动第二套实例。重启后的常见情况是这些端口均未监听。

执行准备：

```bash
cd /data/SuperDataScalpel
./start-local-test69.sh --prepare --threads 2
```

后台启动并保存 PID：

```bash
mkdir -p /data/datascalpel-test69/runtime

nohup ./start-local-test69.sh --threads 2 \
  > /data/datascalpel-test69/runtime/start-local-test69.log \
  2>&1 </dev/null &

app_pid=$!
printf '%s\n' "$app_pid" \
  > /data/datascalpel-test69/runtime/start-local-test69.pid
disown "$app_pid" 2>/dev/null || true
```

观察日志：

```bash
tail -f /data/datascalpel-test69/runtime/start-local-test69.log
```

日志显示五个组件就绪后，退出 `tail` 不会停止后台应用。

停止五个应用时执行：

```bash
cd /data/SuperDataScalpel
./stop-local-test69.sh
```

停止脚本会校验 PID 文件和进程的 test69 运行标记；PID 文件缺失或陈旧时会安全
扫描当前仓库的 test69 进程。它不会停止 Kafka、Kong、DSH 或
`datascalpel-compute-engine` 容器，重复执行也不会报错。

### 5. 健康检查

```bash
curl -fsS http://127.0.0.1:18080/actuator/health
curl -fsS http://127.0.0.1:8081/actuator/health
curl -fsS http://127.0.0.1:18091/health/ready
curl -fsS http://127.0.0.1:18092/health/ready
curl -fsS http://127.0.0.1:18887/ >/dev/null

docker inspect --format '{{.State.Health.Status}}' datascalpel-test69-dsh
docker inspect --format '{{.State.Health.Status}}' datascalpel-test69-kafka
docker inspect --format '{{.State.Health.Status}}' datascalpel-test69-kong
```

五个 HTTP 地址均应成功，三个容器应为 `healthy`。同时确认
`datascalpel-compute-engine` 仍在运行。

### 6. 配置 DSH 模型

DSH 每次启动会在日志输出一次带 Token 的原生访问 URL。只在服务器终端读取，
不要把 Token 写入报告或聊天：

```bash
docker logs --tail 200 datascalpel-test69-dsh 2>&1 \
  | grep -E 'token=' \
  | tail -1
```

把日志 URL 的主机替换为 `10.0.0.69`，端口保持 `13080`，在用户浏览器访问。
首次进入后：

1. 选择 `/workspace` 作为工作目录。
2. 打开 **Settings → Models**。
3. 由用户填写真实模型提供方、模型 ID、Base URL 和 API Key。
4. 保存并确认模型可用。

模型 API Key 尚未提供。如果验证到这里受阻，应明确让用户在上述页面配置，不能
编造、复用未知凭据或从其他服务中窃取密钥。

### 7. 验证 DataScalpel AI 助手

打开 `http://10.0.0.69:18887`，使用 `test69admin` 登录。密码从
`runtime.env` 本地读取，不得输出到对话。

验证：

1. 页面顶部存在“AI 助手”入口。
2. 打开后不再提示“尚未启用”。
3. 能创建工作区和新会话。
4. 配置模型后能发送一条真实消息并收到回复。
5. DSH 能通过 `host.docker.internal:18080/system-mcp` 调用系统 MCP。
6. Admin 日志没有 Bridge 认证失败、密钥解密失败或 DSH 连接错误。

如果需要调用 `/api/v1/dsh/capabilities`，必须使用当前登录用户 JWT；不要把 JWT
打印到终端或报告。期望 `enabled=true`。模型未配置前 `ready=false` 可以作为明确的
剩余边界；模型配置后应达到 ready 状态。

## 验收结果

完成后向用户简洁报告：

- 当前 Git 提交。
- `datascalpel-test69-dsh` Stack 已由 Portainer 管理且健康。
- 五个应用和 Kafka、Kong、Compute Engine 的状态。
- AI 助手是否 enabled/ready，是否完成真实对话。
- UI、DSH 和 Portainer 访问地址。
- 模型未配置时，只报告“需要在 Settings → Models 配置”，不要透露任何 Token。

不要在报告中包含密码、密钥、JWT、DSH 访问 Token或模型 API Key。
