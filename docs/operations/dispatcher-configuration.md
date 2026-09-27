# Dispatcher 默认配置与实例配置

状态：2026-09-27 代码已改造，102 Dispatcher 已切换；实际联调进展和未验证边界见[验证记录](../verification/dispatcher-shared-topics-20260927.md)，不能仅凭本文视为全链路已验收。

## 两种启动方式，同一份实例文件

- 模块 `src/main/resources/application.yml` 保存默认值，并以 `spring.profiles.include: instance` 固定加载实例配置。
- `src/main/resources/application-instance.yml` 是本机 IDEA 编辑入口，含连接配置和禁用的三类目标示例。
- 服务器复制同一份文件到启动工作目录的 `config/application-instance.yml`；执行 `java -jar dispatcher.jar`。不需要 additional-location。系统服务和容器必须固定 WorkingDirectory / working_dir。
- 外部同名文件覆盖内部同名配置；未写项继续使用默认值。环境变量和命令行仍可能有更高优先级，排障时须检查启动参数。
- 后端参数优先级：目标显式配置 > backend-defaults 对应类型 > Java 配置对象的兜底默认值。其他普通参数遵循 Spring 标准优先级。
- `targets` 是 Map，Spring 合并而非整块替换。内置三个示例均 enabled=false。若曾在源码启用调试目标，发布前关闭，或在外置文件同名键下明确禁用；不能靠省略键删除目标。
- 不在仓库、镜像或文档保存真实密码。外置配置限制为运行账号可读。
- 每实例独立账本 Schema 和持久化实例 ID；不因端口、地址别名或重启更换实例身份。

## 实例通道与目标

### 精简维护原则

- 内置默认文件保留超时、轮询、并发等技术参数；实例文件只列连接、认证、消息通道和执行目标。需要调整任意默认值时，按原路径在实例文件中覆盖，不需要修改 JAR。
- 内置实例文件保留三个关闭的目标示例供本机调试参考。服务器外置文件仅需列出实际目标，不必重复写三个示例的 `enabled: false`；前提是打包时未在源码启用这些示例。
- 各目标的 `backend` 固定声明为 `LOCAL_DOCKER`、`KUBERNETES` 或 `YARN`，不共用旧的全局后端环境变量。
- Admin 事件 Topic、制品存储通用默认项、Runner Kafka 通用默认项只在默认文件维护；既有环境变量别名继续生效。实际环境使用非默认值时，在实例文件显式保留。
- 配置不是热更新。改变有效值后需重启；仅删除与当前默认值一致的冗余项，不改变运行中的服务。

### 102 当前部署：应用参数只维护实例文件

- `/data/datascalpel-compute-engine/compose/application-instance.yml` 是应用参数的唯一外置维护入口。
- 同目录 `compose.shared.yaml` 仅负责镜像、JAR 启动命令、端口、目录挂载、重启与健康检查；environment 仅保留 `JAVA_TOOL_OPTIONS` 和 `TZ`，不再重复注入 `DATASCALPEL_*` 应用变量。
- 删除 Compose environment 后，需要通过该 Compose 文件重建容器，普通 restart 不会清除旧容器环境。操作前确认没有活动执行、待清理资源或未投递消息。
- 程序仍兼容环境变量部署方式，但本实例不混用。仓库原有 linux69 自动部署脚本及其 Compose 模板仍采用环境变量部署方式，不能直接用于覆盖这套 102 配置；本次仅调整 102 已部署实例，没有改造或运行该旧脚本。

外置 `data-scalpel.dispatcher.messaging` 包含 `command-topic`、`runner-event-topic`，两项必须由部署方选择稳定且在其他实例未占用的名称。
`admin-event-topic` 默认 `datascalpel.execution.event`，同一平台所有 Dispatcher 通常一致，必须被 Admin 监听。
`runner-control-topic` 不另设配置，由 runner-event-topic 加 `.control` 推导；流式 Runner 仍按执行身份独立消费组并验证 engineId/executionId/runId/attempt/deploymentId。
`ensure-topics` 保留原语义：false 时由运维预建；true 时程序可创建所需四个通道。

发现协议 v3 返回 messaging，Admin 保存已确认通道快照，不再为新模式生成 UUID Topic。
任务仍绑定 engineId，经注册关系定位 targetKey。Kafka 分区 Key **继续使用 executionId**，不为共享通道改成 targetKey 或 engineId；同次执行的控制命令顺序保持原语义。
命令、Runner 各一套实例监听器，消费组包含持久化实例 ID。引擎暂停/恢复/停用不关闭公共消费者。
实例内队列、容量、目标健康仍独立；共享分区和 JVM 不承诺硬吞吐隔离。

## 任务资源策略（按目标维护）

外置实例配置在 `data-scalpel.dispatcher.targets.<目标 key>.resource-policy` 下维护默认值和单次上限。Admin 发现后只读显示，不再在注册页填写这些数值；任务选择默认或自定义，自定义受上限约束。

```yaml
resource-policy:
  defaults:
    driver-cores: 1
    driver-memory-mi-b: 2048
    executor-instances: 2
    executor-cores: 2
    executor-memory-mi-b: 2048
  maximums:
    driver-cores: 8
    driver-memory-mi-b: 16384
    executor-instances: 20
    executor-cores: 8
    executor-memory-mi-b: 16384
```

以上仅为 K8s/YARN 模板，不是探测容量或生产容量建议。Local Docker 外置配置只写 Driver CPU/内存，不配置 Executor。内部五字段传输契约暂保留 1 / 1 / 1024 占位以兼容历史消息，不代表独立 Executor，不参与 Local Docker 上限校验。集群目标显式配置策略时，必须完整提供五项；缺失报错。完整示例已同步程序内 application-instance.yml 和部署模板；新命名目标建议完整复制策略块，避免依赖程序兜底值。同名内置目标可仅覆盖所需字段。

- `resource-policy` 是平台任务资源的权威来源；旧后端 `local-docker.memory/cpus`、`kubernetes.driver-memory/executor-*`、`yarn.driver-memory/executor-*/num-executors` 不参与当前提交，已从默认 YAML 移除。Java 属性仅兼容旧绑定，不是第二套生效资源设置；不要再维护这些项。
- 未声明整个策略时保留程序内后端默认策略以兼容已有目标；部署环境应显式配置。默认值不得超过上限；非法配置启动失败。
- 修改后需重启 Dispatcher；已有引擎应先停止新任务、完成现有运行，再停用注册并重新注册以同步 Admin 快照。无需删除引擎或任务。
- 任务资源配置保持默认模式时，在提交时解析引擎默认值；自定义模式只读任务已保存值。立即运行不接受临时覆盖。
- 运行详情的资源是申请快照，不是实际 CPU/内存使用量。既有排队或运行快照不被改写。
- 集群内存值是 JVM 堆内存，容器还会申请非堆开销；K8s CPU 是 request，并非默认硬限制。不要据此把单项上限理解为物理总额度。三后端逐项核对见[资源审计记录](../verification/dispatcher-resource-audit-20260927.md)。

## 配置项目完整对照

下面逐项从改造前 application.yml 核对。未移位项继续有效，仍生效的技术默认值保留原 DATASCALPEL_* 环境变量别名；下表旧资源项已被 resource-policy 取代，不再参与当前提交。
三种后端由 targets 显式启用，旧 DATASCALPEL_TASK_DISPATCHER_BACKEND 不再用于新模式选目标。单目标路径配置保留在实例文件中；目标独有参数可直接写 YAML，不要求改成环境变量。
部署方优先维护外置 YAML；既有环境变量仍可沿用，但应避免与 YAML、命令行重复配置同一项。

| 原配置路径 | 新配置路径 | 归属 |
| --- | --- | --- |
| `server.port` | `server.port` | 变动配置 |
| `server.shutdown` | `server.shutdown` | 默认配置（可外置覆盖） |
| `logging.level.org.apache.kafka.common.config` | `logging.level.org.apache.kafka.common.config` | 默认配置（可外置覆盖） |
| `spring.application.name` | `spring.application.name` | 默认配置（可外置覆盖） |
| `spring.datasource.url` | `spring.datasource.url` | 变动配置 |
| `spring.datasource.username` | `spring.datasource.username` | 变动配置 |
| `spring.datasource.password` | `spring.datasource.password` | 变动配置 |
| `spring.datasource.hikari.schema` | `spring.datasource.hikari.schema` | 变动配置 |
| `spring.sql.init.mode` | `spring.sql.init.mode` | 默认配置（可外置覆盖） |
| `spring.jpa.open-in-view` | `spring.jpa.open-in-view` | 默认配置（可外置覆盖） |
| `spring.jpa.hibernate.ddl-auto` | `spring.jpa.hibernate.ddl-auto` | 默认配置（可外置覆盖） |
| `spring.jpa.properties.hibernate.default_schema` | `spring.jpa.properties.hibernate.default_schema` | 变动配置 |
| `spring.jpa.properties.hibernate.hbm2ddl.create_namespaces` | `spring.jpa.properties.hibernate.hbm2ddl.create_namespaces` | 默认配置（可外置覆盖） |
| `spring.jpa.properties.hibernate.jdbc.time_zone` | `spring.jpa.properties.hibernate.jdbc.time_zone` | 默认配置（可外置覆盖） |
| `spring.kafka.bootstrap-servers` | `spring.kafka.bootstrap-servers` | 变动配置 |
| `spring.kafka.properties.request.timeout.ms` | `spring.kafka.properties.request.timeout.ms` | 默认配置（可外置覆盖） |
| `spring.kafka.properties.socket.connection.setup.timeout.ms` | `spring.kafka.properties.socket.connection.setup.timeout.ms` | 默认配置（可外置覆盖） |
| `spring.kafka.properties.socket.connection.setup.timeout.max.ms` | `spring.kafka.properties.socket.connection.setup.timeout.max.ms` | 默认配置（可外置覆盖） |
| `spring.kafka.admin.properties.default.api.timeout.ms` | `spring.kafka.admin.properties.default.api.timeout.ms` | 默认配置（可外置覆盖） |
| `spring.kafka.producer.acks` | `spring.kafka.producer.acks` | 默认配置（可外置覆盖） |
| `spring.kafka.producer.properties.enable.idempotence` | `spring.kafka.producer.properties.enable.idempotence` | 默认配置（可外置覆盖） |
| `spring.kafka.producer.properties.delivery.timeout.ms` | `spring.kafka.producer.properties.delivery.timeout.ms` | 默认配置（可外置覆盖） |
| `spring.kafka.producer.properties.max.block.ms` | `spring.kafka.producer.properties.max.block.ms` | 默认配置（可外置覆盖） |
| `spring.kafka.consumer.enable-auto-commit` | `spring.kafka.consumer.enable-auto-commit` | 默认配置（可外置覆盖） |
| `spring.kafka.consumer.auto-offset-reset` | `spring.kafka.consumer.auto-offset-reset` | 默认配置（可外置覆盖） |
| `spring.kafka.consumer.properties.default.api.timeout.ms` | `spring.kafka.consumer.properties.default.api.timeout.ms` | 默认配置（可外置覆盖） |
| `spring.kafka.listener.ack-mode` | `spring.kafka.listener.ack-mode` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.token` | `data-scalpel.dispatcher.token` | 变动配置 |
| `data-scalpel.dispatcher.backend` | `data-scalpel.dispatcher.targets.<key>.backend` | 旧顶层项仅显式兼容模式 |
| `data-scalpel.dispatcher.ensure-topics` | `data-scalpel.dispatcher.ensure-topics` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.admission-poll-interval` | `data-scalpel.dispatcher.admission-poll-interval` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.observation-poll-interval` | `data-scalpel.dispatcher.observation-poll-interval` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.outbox-poll-interval` | `data-scalpel.dispatcher.outbox-poll-interval` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.outbox-claim-timeout` | `data-scalpel.dispatcher.outbox-claim-timeout` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.submission-uncertain-grace` | `data-scalpel.dispatcher.submission-uncertain-grace` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.observation-failure-grace` | `data-scalpel.dispatcher.observation-failure-grace` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.deactivation-timeout` | `data-scalpel.dispatcher.deactivation-timeout` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.send-timeout` | `data-scalpel.dispatcher.send-timeout` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.log-max-bytes` | `data-scalpel.dispatcher.log-max-bytes` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.artifact.endpoint` | `data-scalpel.dispatcher.artifact.endpoint` | 变动配置 |
| `data-scalpel.dispatcher.artifact.runner-endpoint` | `data-scalpel.dispatcher.artifact.runner-endpoint` | 变动配置 |
| `data-scalpel.dispatcher.artifact.region` | `data-scalpel.dispatcher.artifact.region` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.artifact.bucket` | `data-scalpel.dispatcher.artifact.bucket` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.artifact.root-prefix` | `data-scalpel.dispatcher.artifact.root-prefix` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.artifact.access-key` | `data-scalpel.dispatcher.artifact.access-key` | 变动配置 |
| `data-scalpel.dispatcher.artifact.secret-key` | `data-scalpel.dispatcher.artifact.secret-key` | 变动配置 |
| `data-scalpel.dispatcher.artifact.path-style-access` | `data-scalpel.dispatcher.artifact.path-style-access` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.artifact.url-lifetime` | `data-scalpel.dispatcher.artifact.url-lifetime` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.artifact.maximum-manifest-bytes` | `data-scalpel.dispatcher.artifact.maximum-manifest-bytes` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.artifact.maximum-result-bytes` | `data-scalpel.dispatcher.artifact.maximum-result-bytes` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.artifact.result-availability-grace` | `data-scalpel.dispatcher.artifact.result-availability-grace` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.local-docker.executable` | `data-scalpel.dispatcher.backend-defaults.local-docker.executable` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.local-docker.image` | `data-scalpel.dispatcher.targets.<key>.local-docker.image` | 目标配置 |
| `data-scalpel.dispatcher.local-docker.platform` | `data-scalpel.dispatcher.targets.<key>.local-docker.platform` | 目标配置 |
| `data-scalpel.dispatcher.local-docker.pull` | `data-scalpel.dispatcher.backend-defaults.local-docker.pull` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.local-docker.memory` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.local-docker.cpus` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.local-docker.runner-jar` | `data-scalpel.dispatcher.targets.<key>.local-docker.runner-jar` | 目标配置 |
| `data-scalpel.dispatcher.local-docker.work-directory` | `data-scalpel.dispatcher.targets.<key>.local-docker.work-directory` | 目标配置 |
| `data-scalpel.dispatcher.local-docker.checkpoint-directory` | `data-scalpel.dispatcher.targets.<key>.local-docker.checkpoint-directory` | 目标配置 |
| `data-scalpel.dispatcher.local-docker.command-timeout` | `data-scalpel.dispatcher.backend-defaults.local-docker.command-timeout` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.local-docker.stop-timeout` | `data-scalpel.dispatcher.backend-defaults.local-docker.stop-timeout` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.local-docker.runner-java-options` | `data-scalpel.dispatcher.backend-defaults.local-docker.runner-java-options` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.yarn.spark-submit` | `data-scalpel.dispatcher.backend-defaults.yarn.spark-submit` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.yarn.yarn` | `data-scalpel.dispatcher.backend-defaults.yarn.yarn` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.yarn.hdfs` | `data-scalpel.dispatcher.backend-defaults.yarn.hdfs` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.yarn.deploy-mode` | `data-scalpel.dispatcher.backend-defaults.yarn.deploy-mode` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.yarn.queue` | `data-scalpel.dispatcher.targets.<key>.yarn.queue` | 目标配置 |
| `data-scalpel.dispatcher.yarn.runner-jar` | `data-scalpel.dispatcher.targets.<key>.yarn.runner-jar` | 目标配置 |
| `data-scalpel.dispatcher.yarn.driver-memory` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.yarn.executor-memory` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.yarn.executor-cores` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.yarn.num-executors` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.yarn.submit-timeout` | `data-scalpel.dispatcher.backend-defaults.yarn.submit-timeout` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.yarn.command-timeout` | `data-scalpel.dispatcher.backend-defaults.yarn.command-timeout` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.yarn.work-directory` | `data-scalpel.dispatcher.targets.<key>.yarn.work-directory` | 目标配置 |
| `data-scalpel.dispatcher.kubernetes.kubeconfig` | `data-scalpel.dispatcher.targets.<key>.kubernetes.kubeconfig` | 目标配置 |
| `data-scalpel.dispatcher.kubernetes.spark-submit` | `data-scalpel.dispatcher.backend-defaults.kubernetes.spark-submit` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.kubernetes.kubectl` | `data-scalpel.dispatcher.backend-defaults.kubernetes.kubectl` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.kubernetes.master` | `data-scalpel.dispatcher.targets.<key>.kubernetes.master` | 目标配置 |
| `data-scalpel.dispatcher.kubernetes.namespace` | `data-scalpel.dispatcher.targets.<key>.kubernetes.namespace` | 目标配置 |
| `data-scalpel.dispatcher.kubernetes.service-account` | `data-scalpel.dispatcher.targets.<key>.kubernetes.service-account` | 目标配置 |
| `data-scalpel.dispatcher.kubernetes.image` | `data-scalpel.dispatcher.targets.<key>.kubernetes.image` | 目标配置 |
| `data-scalpel.dispatcher.kubernetes.driver-memory` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.kubernetes.executor-memory` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.kubernetes.executor-cores` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.kubernetes.executor-instances` | `data-scalpel.dispatcher.targets.<key>.resource-policy` | 旧项不参与当前提交；按语义迁移到目标策略 |
| `data-scalpel.dispatcher.kubernetes.submit-timeout` | `data-scalpel.dispatcher.backend-defaults.kubernetes.submit-timeout` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.kubernetes.command-timeout` | `data-scalpel.dispatcher.backend-defaults.kubernetes.command-timeout` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.kubernetes.cancel-grace-seconds` | `data-scalpel.dispatcher.backend-defaults.kubernetes.cancel-grace-seconds` | 后端默认；单目标同名项优先 |
| `data-scalpel.dispatcher.kubernetes.work-directory` | `data-scalpel.dispatcher.targets.<key>.kubernetes.work-directory` | 目标配置 |
| `data-scalpel.dispatcher.runner-kafka.bootstrap-servers` | `data-scalpel.dispatcher.runner-kafka.bootstrap-servers` | 变动配置 |
| `data-scalpel.dispatcher.runner-kafka.security-protocol` | `data-scalpel.dispatcher.runner-kafka.security-protocol` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.runner-kafka.client-id-prefix` | `data-scalpel.dispatcher.runner-kafka.client-id-prefix` | 默认配置（可外置覆盖） |
| `data-scalpel.dispatcher.streaming.checkpoint-base-uri` | `data-scalpel.dispatcher.targets.<key>.streaming.checkpoint-base-uri` | 目标配置，不跨目标继承 |
| `management.endpoints.web.exposure.include` | `management.endpoints.web.exposure.include` | 默认配置（可外置覆盖） |
| `management.endpoint.health.show-details` | `management.endpoint.health.show-details` | 默认配置（可外置覆盖） |

另外保留上一版多目标新增参数：
- 实例：max-concurrent-submissions、maintenance-concurrency。
- targets.<key>：enabled、name、backend，以及对应后端配置、streaming.checkpoint-base-uri。
- YARN：hadoop-conf-directory、cluster-id。启用时必须配置，凭据与环境按目标传入。
- Kubernetes：kubeconfig。镜像仍要求不可变 digest；不把认证内容返回发现接口。
- Runner Kafka 仍只有 bootstrap-servers、security-protocol、client-id-prefix 三个现有字段。本次不声称扩展 SASL 凭据传递能力。

## 旧配置与迁移

旧单后端仅在显式 `legacy-single-backend: true` 时使用顶层 backend/local-docker/yarn/kubernetes/streaming。
旧引擎独占消息模式仅在 `messaging.scope: LEGACY_ENGINE` 时保留；新模板均使用 INSTANCE。
这两个兼容开关只用于维护窗口和回退，不是日常部署推荐项。
旧版 Admin 不能直接登记 v3 共享通道；升级 Dispatcher 前同步 Admin 并迁移原记录。

升级必须：
1. 停止新增任务，确认 Admin 无排队/活动运行及未投递执行命令；Dispatcher 无活动执行、待清理外部资源、未投递事件。
2. 备份双方注册记录、原 Topic/消费组位点、配置与程序包。确认旧消费者已消费到 Broker 末尾。
3. 停止旧 Dispatcher；停止旧 Admin 或确保其不会再次创建旧唯一约束、发送旧通道命令。
4. 移除 compute_engine 的 uk_compute_engine_command_topic、uk_compute_engine_runner_event_topic 两个旧唯一约束。保留引擎名称及实例+目标绑定唯一约束。ddl-auto=update 不会代替这一步。
5. 按明确实例和引擎 ID 更新 Admin/Dispatcher 的通道快照，保留 ID、目标指纹、任务关联及全部执行历史。不得批量改写历史 Outbox 的目标 Topic。
6. 配置新实例通道，启动新 Dispatcher 与 Admin，发现核对，然后进行真实 Docker/K8s 回归。
7. 不自动删除旧 Topic；保留到验收及回退窗口结束。若切换后已运行新任务，不能简单恢复旧数据库快照覆盖新历史。

旧注册仍使用其他通道时，新 Dispatcher 会明确拒绝以 INSTANCE 模式启动，防止悄悄改路由。
