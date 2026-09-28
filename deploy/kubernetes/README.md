# Kubernetes Runner 镜像

用途：Spark 原生 Kubernetes cluster mode，不需要 Spark Operator。该目录提供 Runner 制品构建，不代表 Admin 注册、Dispatcher 多目标或真实任务全链路已验收。

## 构建

在工程根目录使用 Java 21 和工程 Maven Wrapper：

```powershell
.\mvnw.cmd -pl data-scalpel-task-engine -am -Ptask-engine-full-package '-DskipTests' package
```

在可用 Docker 的构建主机上，以工程根目录为上下文执行：

```bash
docker build -f deploy/kubernetes/runner.Dockerfile \
  --build-arg SPARK_BASE_IMAGE='<受控仓库的 Spark 4.1.1 / Scala 2.13 / Java 21 镜像>@sha256:<实际摘要>' \
  -t '<内部仓库>/datascalpel-spark-runner:<制品版本>' .
```

镜像只复制 full-package 产出的 `runner-cluster.jar`，不得替换成包含整套 Spark 的 `runner-local.jar`。继承 Spark 基础镜像的 Kubernetes entrypoint，使用非 root UID 185。镜像里不放 launch.json、kubeconfig、Kafka/MinIO/数据库密码。

发布到内部仓库后，Dispatcher 的 `kubernetes.image` 使用实际 **仓库 manifest digest**，不要把 `docker image inspect` 的本地 image ID 当作仓库 digest。离线单节点验证可以预加载镜像，但必须核对容器运行时内的 manifest digest 和完整引用，并确认 Pod 没有改拉同名可变 tag。

## 版本和权限

- Kubernetes 至少 1.32（Spark 4.1.1 要求）；同时核对 OS、内核、容器运行时及管理平台支持范围。
- Dispatcher 在集群外使用显式受限 `kubernetes.kubeconfig`，Driver 使用目标 Namespace 内的 `spark-runner` ServiceAccount。
- [RBAC 示例](rbac.example.yaml) 限制到 `datascalpel-test`；其他环境需统一替换 Namespace 及对应 ClusterRole 名称。Service/ConfigMap 的 `patch` 是 Spark 4.1.1 server-side apply 所需，launch Secret 不要求 patch。该示例不包含 PVC、Kerberos 或其他可选 Spark 特性的权限。
- Driver/Executor 需要访问 Kafka、MinIO 和任务实际使用的数据源；基于网络可达性检查不能代替真实任务验证。
- 当前后端固定关闭 Driver PVC 所有权和复用，使用 `emptyDir` 临时存储；直接手工 `spark-submit` 验证时也需设置 `spark.kubernetes.driver.ownPersistentVolumeClaim=false`、`spark.kubernetes.driver.reusePersistentVolumeClaim=false`。否则 Spark 4.1.1 默认 PVC 清理会因示例未授予 PVC 删除权限而报错。
- 单节点试验先限制一个在途应用和一个 Executor，计入 Driver/Executor memory overhead，并保留镜像及临时文件空间。

详细配置、RBAC、Secret 及生命周期规则见 [Kubernetes 后端设计](../../docs/design/task-execution-platform/10-kubernetes-cluster-backend.md)。特定测试环境和实际结果见 [103 环境记录](../../docs/operations/k8s103-single-node-test.md)、[联调记录](../../docs/verification/kubernetes-submission-20260926.md)。
