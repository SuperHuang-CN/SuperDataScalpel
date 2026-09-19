# linux69 测试环境基础设施

`compose.yaml` 作为 Portainer Stack `datascalpel-test69-infra` 部署 Kafka 和 Kong。
PostgreSQL 与 MinIO 复用 linux5，但使用独立数据库、角色、bucket 和权限策略。

私密配置只保存在 linux69 的
`/data/datascalpel-test69/config/runtime.env`。从 `runtime.env.example` 创建该文件，
使用 `openssl rand -hex 24` 生成密码和 Token，使用 `openssl rand -base64 32`
生成加密主密钥，然后设置权限 `600`。

在 linux5 的 PostgreSQL 容器中执行初始化脚本时，使用容器已有的超级用户：

```bash
docker exec -i postgresql16 sh -c 'exec psql --username "$POSTGRES_USER" --dbname postgres \
  --set=admin_password="$1" --set=engine_password="$2" --set=kong_password="$3"' sh \
  '<DATASCALPEL_DB_PASSWORD>' \
  '<DATASCALPEL_ENGINE_DB_PASSWORD>' \
  '<KONG_PG_PASSWORD>' \
  < bootstrap-postgresql.sql
```

MinIO 初始化使用容器已有的管理员环境变量；其中第三个参数应替换为
`runtime.env` 中的 `DATASCALPEL_FILE_STORAGE_SECRET_KEY`：

```bash
docker cp minio-policy.json minio:/tmp/datascalpel-test69-policy.json
docker exec minio sh -c 'mc alias set local http://127.0.0.1:9000 \
  "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null && \
  mc mb --ignore-existing local/datascalpel-test69 && \
  mc admin user add local datascalpel-test69 "$1" && \
  mc admin policy create local datascalpel-test69 \
    /tmp/datascalpel-test69-policy.json && \
  mc admin policy attach local datascalpel-test69 --user datascalpel-test69' \
  sh '<DATASCALPEL_FILE_STORAGE_SECRET_KEY>'
```

Portainer Stack 环境变量来自 `stack.env.example`；其中
`KONG_PG_PASSWORD` 与 `runtime.env` 保持一致。部署完成后，从仓库根目录运行：

```bash
./start-local-test69.sh --prepare
./start-local-test69.sh
```

脚本以前台方式托管五个应用进程，按 `Ctrl+C` 停止应用；Kafka 和 Kong 继续由
Portainer 管理。需要从另一个终端停止这五个应用时，在仓库根目录执行：

```bash
./stop-local-test69.sh
```

停止脚本可处理缺失或陈旧的 PID 文件；它只停止 Admin、Service Engine、Task
Engine、Dispatcher 和前端，不停止 Kafka、Kong、DSH 或
`datascalpel-compute-engine` 容器。重复执行时若应用未启动，会直接成功返回。

`dsh-compose.yaml` 作为独立 Portainer Stack `datascalpel-test69-dsh` 部署
AI 助手运行时。先在 linux69 构建 `datascalpel-dsh:0.1.5-rc.1`，再把
`runtime.env` 中的 `DATASCALPEL_DSH_BRIDGE_KEY` 原样写入权限为 `600` 的
`/data/datascalpel-test69/config/dsh-admin-bridge-token`。Stack 只读挂载该
密钥文件，Compose 和 Portainer 环境变量不保存秘密值。从系统 MCP 创建绑定指定系统用户的独立访问令牌，将完整秘密以 `600` 权限保存到 `/data/datascalpel-test69/config/dsh-system-mcp-token`；该令牌只供 DSH 原生页面的 `DataScalpel 个人助手` Preset 使用，Admin AI 助手继续按当前用户动态注入托管凭据。DSH 的原生配置页面
发布在 `http://10.0.0.69:13080`，模型凭据在 **Settings → Models** 中配置。
