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
Portainer 管理。
