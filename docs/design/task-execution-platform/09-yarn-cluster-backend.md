# 09 YARN Cluster 后端开发设计

## 1. 目标

实现 `YarnSparkExecutionBackend`，由固定 Dispatcher 主机使用 Spark Client把 Runner提交到 YARN cluster。Driver运行在 YARN ApplicationMaster容器中，Dispatcher退出不影响已提交应用。

依赖：[08 Local Docker 端到端验收](08-local-docker-end-to-end.md)。在 Local链路稳定前不开发 YARN，避免同时调试消息可靠性和集群提交。

## 2. 固定部署决定

```text
master      yarn
deployMode  cluster
```

不支持 YARN client mode。一个 YARN Dispatcher部署只连接一套固定 Hadoop/YARN集群。

Dispatcher 主机需要：

- Java 21。
- Spark 4.1.1 Client，Scala 2.13。
- 兼容目标集群的 Hadoop/YARN Client。
- `HADOOP_CONF_DIR` 或 `YARN_CONF_DIR`。
- 可执行 `spark-submit`、`yarn application` 和 `yarn logs`。
- 访问 Kafka、MinIO、Dispatcher PostgreSQL和 ResourceManager。

## 3. 配置

```properties
data-scalpel.dispatcher.backend=YARN
data-scalpel.dispatcher.yarn.spark-submit=/opt/spark/bin/spark-submit
data-scalpel.dispatcher.yarn.yarn=/opt/hadoop/bin/yarn
data-scalpel.dispatcher.yarn.hdfs=/opt/hadoop/bin/hdfs
data-scalpel.dispatcher.yarn.deploy-mode=cluster
data-scalpel.dispatcher.yarn.queue=default
data-scalpel.dispatcher.yarn.runner-jar=hdfs:///datascalpel/runner/task-runner-cluster.jar
data-scalpel.dispatcher.yarn.driver-memory=2g
data-scalpel.dispatcher.yarn.executor-memory=2g
data-scalpel.dispatcher.yarn.executor-cores=2
data-scalpel.dispatcher.yarn.num-executors=2
data-scalpel.dispatcher.yarn.submit-timeout=5m
data-scalpel.dispatcher.yarn.command-timeout=2m
data-scalpel.dispatcher.yarn.work-directory=work/task-executions-yarn
```

queue 和资源默认由 Dispatcher部署配置控制。第一阶段不允许 Canvas节点或用户注入任意 Spark配置。

## 4. 提交命令

逻辑命令：

```text
spark-submit
  --master yarn
  --deploy-mode cluster
  --name datascalpel-{executionId}
  --class cn.superhuang.datascalpel.taskengine.runner.TaskRunnerMain
  --queue <configured-queue>
  --driver-memory <configured>
  --executor-memory <configured>
  --executor-cores <configured>
  --num-executors <configured>
  --conf spark.yarn.submit.waitAppCompletion=false
  --conf spark.yarn.tags=datascalpel-execution-{executionId}
  --files <secure-launch-file>#launch.json
  hdfs:///datascalpel/runner/task-runner-cluster.jar
```

要求：

- 使用 `ProcessBuilder(List<String>)`。
- executionId不进入不受控 Shell。
- `spark.yarn.submit.waitAppCompletion=false` 最后追加，不能被额外参数覆盖。
- `--master` 和 `--deploy-mode` 固定，不能由任务定义覆盖。
- launch内容、预签名 URL和 Kafka密码不进入参数或提交日志。

## 5. Launch 文件和凭据

Dispatcher 在权限受限工作目录生成 launch.json：

- 文件权限仅 Dispatcher用户可读。
- manifest/result URL在出队提交前生成。
- 通过 `--files` 由 YARN本地化到 Driver。
- Runner从当前目录 `launch.json` 读取。

Kafka安全配置优先通过目标集群已有 Hadoop Credential Provider、受控配置文件或 YARN本地化受限文件提供。第一阶段允许内网 PLAINTEXT；启用 SASL时禁止把密码直接写入 spark-submit命令。

YARN staging目录和聚合日志可能保留本地化文件名，因此文件名不得包含秘密，预签名 URL必须短期有效，Bucket保持私有。

## 6. Application ID 与 Handle

`spark-submit` 返回后从标准输出解析：

```text
application_<clusterTimestamp>_<sequence>
```

解析规则：

- 使用严格正则。
- 多个不同 Application ID视为歧义并失败。
- 找到 ID后立即持久化为 externalExecutionId。
- 提交日志必须脱敏后才能保存。

稳定恢复标签：

```text
datascalpel-execution-{executionId}
```

如果 Dispatcher在获得 Application ID前崩溃，重启后用 YARN application tag查询。只接受唯一匹配；多匹配进入 LOST并要求人工处理，不能任选一个。

## 7. 状态观测

使用：

```text
yarn application -status <applicationId>
```

映射：

| YARN State | Final State | Backend状态 |
| --- | --- | --- |
| NEW/NEW_SAVING/SUBMITTED/ACCEPTED | UNDEFINED | PENDING |
| RUNNING | UNDEFINED | RUNNING |
| FINISHED | SUCCEEDED | SUCCEEDED |
| FINISHED | FAILED | FAILED |
| FINISHED | KILLED | CANCELLED或TIMED_OUT |
| FAILED | 任意 | FAILED |
| KILLED | 任意 | CANCELLED或TIMED_OUT |

YARN显示 SUCCEEDED 只说明 Driver进程正常退出。Dispatcher仍需读取并验证 result.json；缺少结果时等待短宽限期后标记 `RUNNER_RESULT_MISSING`。

Tracking URL从 YARN状态中提取，校验长度和协议后通过 Admin事件返回。

## 8. 取消

```text
yarn application -kill <applicationId>
```

规则：

- QUEUED在 Dispatcher内部取消，不调用 YARN。
- SUBMITTING无 ID时设置补偿标志；找到 ID后立即 kill。
- RUNNING kill后继续轮询直到 KILLED或超时。
- YARN已经 FINISHED时读取真实 final status，不能强行改成 CANCELLED。
- deadline触发的 kill最终映射 TIMED_OUT，而用户取消映射 CANCELLED。

## 9. 日志

终态后执行：

```text
yarn logs -applicationId <applicationId>
```

- 设定命令超时和最大 20 MiB。
- 保留 Driver和 Executor关键日志，超出时截断。
- 先上传 `console.log`，再发布终态 Admin事件。
- 日志命令失败不覆盖已经验证的任务成功结果，但事件增加日志获取警告。

## 10. 重启恢复

- QUEUED：继续准入。
- SUBMITTING有 ID：转 SUBMITTED并观测。
- SUBMITTING无 ID：按 execution tag查找。
- SUBMITTED/RUNNING：按 Application ID观测。
- CANCEL_REQUESTED：继续 kill和观测。
- YARN已终态但账本未终态：读取 result并完成收敛。
- ResourceManager暂时不可用：保持当前非终态并记录观测失败，不能立即 LOST。
- 超过配置的不可观测宽限期才进入 LOST。

## 11. Readiness

检查：

1. `spark-submit --version` 可执行且版本为 4.1.1。
2. Java版本满足 21。
3. `yarn node -list` 或等价只读命令可连接集群。
4. 使用配置的 HDFS Client 执行 `dfs -test -r`，确认 Runner cluster JAR URI 可读。
5. Kafka、MinIO和 Dispatcher数据库可用。

readiness 不提交测试 Spark Application，避免健康检查产生集群负担。单独的“计算引擎测试”可以提供可选 dry-run检查，但第一阶段不执行真实任务。

## 12. 网络要求

YARN Driver和 Executor至少需要访问：

- manifest/result所用 MinIO Runner Endpoint。
- Kafka Broker地址。
- 任务实际引用的 JDBC 数据库；普通标量读取和 APPEND 的支持范围以 Canvas JDBC 能力矩阵为准。

不要求访问 Admin或 Dispatcher HTTP。

预签名 URL必须使用集群节点可解析的 DNS，不能使用 Dispatcher的 localhost 或 `host.docker.internal`。

## 13. 测试计划

- spark-submit参数、固定 cluster模式和不可覆盖配置。
- Application ID解析、多个 ID歧义和错误输出脱敏。
- YARN状态完整映射。
- tag恢复唯一/无匹配/多匹配。
- 取消和 deadline区分。
- 日志截断、上传顺序和日志失败警告。
- ResourceManager短暂不可用不误判 LOST。
- MiniYARNCluster或专用测试集群完成集成测试。
- 真实验收：YARN Driver执行 PostgreSQL/MySQL Canvas并通过 Kafka/MinIO回传。

## 14. 阶段退出条件

- YARN计算引擎可以注册为 ACTIVE并通过 readiness。
- Runner以 cluster mode运行，Dispatcher重启不影响 Driver。
- submit、inspect、cancel、log和recover完整实现。
- YARN应用终态、result.json和 Admin TaskRun一致。
- 没有 Runner到 Admin/Dispatcher的 HTTP调用。
