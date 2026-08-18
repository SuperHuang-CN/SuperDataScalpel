# 10 Kubernetes Cluster 后端开发设计

## 1. 目标

实现 `KubernetesSparkExecutionBackend`，通过 Spark原生 Kubernetes cluster mode提交一次性 Runner。第一阶段不引入 Spark Operator，Dispatcher使用 `spark-submit` 和 Kubernetes API/`kubectl` 完成提交、观测、取消、日志和恢复。

依赖：[09 YARN Cluster 后端](09-yarn-cluster-backend.md)。Kubernetes复用已经在 Local和 YARN验证过的 Kafka、MinIO、Runner和 Dispatcher状态机。

## 2. 固定部署决定

```text
master      k8s://<api-server>
deployMode  cluster
```

一个 Kubernetes Dispatcher部署固定连接一个 Cluster和 Namespace。单次任务不能提交 kubeconfig、Namespace、镜像、ServiceAccount或任意 Spark Conf。

## 3. Runner 镜像

构建专用镜像，例如：

```text
registry.internal/datascalpel/spark-runner:4.1.1-0.1.0
```

镜像基线：

- Spark 4.1.1。
- Scala 2.13。
- Java 21。
- Task Runner cluster JAR。
- PostgreSQL、MySQL、openGauss、Kingbase、Oracle、SQL Server、ClickHouse、达梦和 TDengine JDBC Driver。
- Kafka Client及必要依赖。
- 非 root运行用户。

镜像必须通过不可变 Digest部署；Tag只用于展示。Dispatcher readiness检查配置了 Digest或受控版本。

Runner镜像不包含数据库密码、MinIO凭据、Kafka生产凭据、kubeconfig或 Admin/Dispatcher Token。

## 4. Dispatcher 配置

```properties
data-scalpel.dispatcher.backend=KUBERNETES
data-scalpel.dispatcher.kubernetes.spark-submit=/opt/spark/bin/spark-submit
data-scalpel.dispatcher.kubernetes.kubectl=/usr/local/bin/kubectl
data-scalpel.dispatcher.kubernetes.master=k8s://https://kubernetes.default.svc
data-scalpel.dispatcher.kubernetes.namespace=datascalpel
data-scalpel.dispatcher.kubernetes.service-account=spark-runner
data-scalpel.dispatcher.kubernetes.image=registry.internal/datascalpel/spark-runner@sha256:...
data-scalpel.dispatcher.kubernetes.driver-memory=2g
data-scalpel.dispatcher.kubernetes.executor-memory=2g
data-scalpel.dispatcher.kubernetes.executor-cores=2
data-scalpel.dispatcher.kubernetes.executor-instances=2
data-scalpel.dispatcher.kubernetes.submit-timeout=5m
data-scalpel.dispatcher.kubernetes.command-timeout=2m
data-scalpel.dispatcher.kubernetes.cancel-grace-seconds=10
data-scalpel.dispatcher.kubernetes.work-directory=work/task-executions-kubernetes
```

Dispatcher可以运行在集群内或集群外。集群内优先使用 ServiceAccount；集群外使用权限受限 kubeconfig文件。

## 5. 稳定 Kubernetes 身份

由 executionId派生合法 DNS Label：

```text
applicationName: ds-{executionId-no-dashes}
driverPodName:   ds-{executionId-no-dashes}-driver
secretName:      ds-{executionId-no-dashes}-launch
```

稳定 Labels：

```text
cn.superhuang.datascalpel/managed=true
cn.superhuang.datascalpel/engine-id=<engineId>
cn.superhuang.datascalpel/execution-id=<executionId>
cn.superhuang.datascalpel/run-id=<runId>
cn.superhuang.datascalpel/attempt=<attempt>
```

Driver和 Executor都设置 execution label；Driver Pod名称作为主要 externalExecutionId，Label作为恢复选择器。

## 6. 临时 Secret

每次提交前创建 Kubernetes Secret：

```text
launch.json
kafka.properties（仅启用认证时）
```

Secret：

- 位于固定 Namespace。
- 带完整 execution Labels。
- 只挂载给本次 Driver。
- 不挂载给 Executor，除非未来执行语义确实需要 Executor直接发送 Kafka。
- 不在 kubectl命令参数中传递内容；通过权限受限临时文件或 Kubernetes Client输入。
- 终态后删除。

Runner当前只在 Driver发布状态，因此 Kafka凭据不需要进入 Executor Pod。

## 7. Spark Submit

逻辑命令：

```text
spark-submit
  --master k8s://...
  --deploy-mode cluster
  --name <applicationName>
  --class cn.superhuang.datascalpel.taskengine.runner.TaskRunnerMain
  --conf spark.kubernetes.namespace=<namespace>
  --conf spark.kubernetes.authenticate.driver.serviceAccountName=<serviceAccount>
  --conf spark.kubernetes.container.image=<immutable-image>
  --conf spark.kubernetes.driver.pod.name=<driverPodName>
  --conf spark.kubernetes.driver.label.<execution-label>=<executionId>
  --conf spark.kubernetes.executor.label.<execution-label>=<executionId>
  --conf spark.kubernetes.driver.secrets.<secretName>=/opt/datascalpel/runtime
  --conf spark.kubernetes.driverEnv.DATASCALPEL_TASK_LAUNCH_FILE=/opt/datascalpel/runtime/launch.json
  --conf spark.kubernetes.driverEnv.DATASCALPEL_TASK_WORK_DIRECTORY=/tmp/datascalpel
  --conf spark.kubernetes.submission.waitAppCompletion=false
  --conf spark.driver.memory=...
  --conf spark.executor.memory=...
  --conf spark.executor.cores=...
  --conf spark.executor.instances=...
  local:///opt/datascalpel/task-runner-cluster.jar
```

固定 master、deploy-mode、namespace、镜像、Pod名称、Labels和 Secret挂载配置最后追加，不能被额外参数覆盖。

第一阶段使用 Spark原生提交，不创建 SparkApplication CRD。

## 8. 提交与 Handle

提交步骤：

1. 创建 launch Secret。
2. 查询是否已存在相同 Driver Pod/Label。
3. 执行 spark-submit。
4. 等待 Driver Pod对象出现，不等待任务完成。
5. 校验 Pod Labels和 Owner身份。
6. 持久化 Driver Pod名称为 externalExecutionId。

如果 spark-submit超时：

- 按固定 Driver Pod名称和 Labels查询。
- 唯一匹配则视为提交成功。
- 无匹配进入提交不确定宽限期。
- 身份冲突或多匹配进入 LOST，不删除未知资源。

## 9. 状态观测

使用 Kubernetes JSON输出，不解析表格文本：

```text
kubectl -n <namespace> get pod <driverPodName> -o json
```

映射：

| Pod状态 | Backend状态 |
| --- | --- |
| Pending | PENDING |
| Running | RUNNING |
| Succeeded | SUCCEEDED |
| Failed | FAILED |
| Terminating | RUNNING/CANCEL_REQUESTED |
| NotFound | UNKNOWN，先按 Label恢复 |

Pod Succeeded后仍需验证 result.json。Pod Failed时优先使用合法 Runner result；没有结果时生成安全的 Kubernetes失败摘要。

禁止把 Pod完整 JSON、环境变量或 Secret引用内容发送给 Admin。

## 10. 取消和清理

取消：

```text
kubectl -n <namespace> delete pod <driverPodName> --grace-period=<configured>
```

并按 execution Label删除残留 Executor Pod。规则：

- 只删除同时具有 managed、engineId和 executionId标签的资源。
- 删除前再次读取并核对标签。
- deadline取消映射 TIMED_OUT，用户取消映射 CANCELLED。
- 终态清理临时 Secret。
- 是否保留已结束 Driver Pod由 Spark/Kubernetes TTL策略控制；Dispatcher不立即删除成功 Pod，先采集日志。

## 11. 日志

Driver日志：

```text
kubectl -n <namespace> logs <driverPodName> --timestamps
```

必要时按 Label收集 Executor日志，但第一阶段默认只收 Driver日志，避免大规模聚合。最大 20 MiB，超出截断。

日志上传 MinIO完成后再发布终态事件。Pod已经被集群回收导致日志缺失时，不覆盖合法 result终态，只增加 `LOG_UNAVAILABLE` 警告。

## 12. RBAC

Dispatcher身份在固定 Namespace需要最小权限：

```text
pods: get/list/create/delete
pods/log: get
secrets: get/create/delete
services: get/create/delete
configmaps: get/create/delete
```

Spark Driver ServiceAccount需要 Spark创建和管理 Executor Pod、Service、ConfigMap所需权限，但不需要访问 Dispatcher数据库或 Admin API。

禁止 Dispatcher使用 cluster-admin。所有 List/Delete操作必须限制 Namespace和稳定 Label。

## 13. 网络要求

Driver和 Executor需要访问：

- Kafka Broker。
- MinIO Runner Endpoint。
- Input/Output PostgreSQL/MySQL。
- Kubernetes API（Spark Driver管理 Executor所需）。

不要求访问 Admin或 Dispatcher HTTP。

预签名 URL使用集群内可解析 DNS；如果 MinIO位于集群内，推荐 ClusterIP Service地址。数据库网络策略只开放实际目标地址和端口。

## 14. 重启恢复

- QUEUED：继续准入。
- SUBMITTING：按固定 Driver Pod名称和 Labels恢复。
- SUBMITTED/RUNNING：读取 Pod JSON恢复状态。
- CANCEL_REQUESTED：继续删除 Driver/Executor并等待消失。
- Driver终态但 Dispatcher未终态：读取 result并上传日志。
- Secret遗留但没有 Pod：超过提交宽限期后删除 Secret并标记 LOST。
- Dispatcher暂时无法访问 API Server：保持当前状态并重试，不立即 LOST。

## 15. Readiness

检查：

1. spark-submit版本为 4.1.1。
2. Kubernetes API可访问。
3. 固定 Namespace存在。
4. Dispatcher 使用 `kubectl auth can-i` 检查 Pod、日志、Secret、Service 和 ConfigMap 所需的最小读写权限。
5. Runner镜像配置非空且符合部署策略。
6. Kafka、MinIO和 Dispatcher数据库可用。

readiness不创建 Pod或 Secret。管理页面“测试”可以额外检查镜像配置，但第一阶段不提交测试 Spark Application。

## 16. 测试计划

- DNS名称和 Label派生。
- 固定 spark-submit参数、不可覆盖配置。
- Secret创建内容不进入日志/命令。
- Pod JSON状态映射。
- 固定名称与 Label恢复、身份冲突保护。
- Cancel只删除受管且身份一致的资源。
- 日志截断和 Pod已删除场景。
- API Server短暂不可用不误判 LOST。
- Kind或专用测试 Cluster完成 Spark Kubernetes集成。
- 真实验收：Kubernetes Driver完成 Canvas JDBC执行，Runner通过 Kafka/MinIO回传，Admin显示终态。

## 17. 阶段退出条件

- Kubernetes计算引擎可以注册、Drain和反注册。
- Runner以 Spark Kubernetes cluster mode运行。
- Dispatcher重启后可以按 Driver Pod/Label恢复。
- submit、inspect、cancel、log和recover全部实现。
- RBAC、Secret和敏感信息边界通过审查。
- Local Docker、YARN、Kubernetes使用同一 Kafka和结果协议，Admin不包含后端特例。
