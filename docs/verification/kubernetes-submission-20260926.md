# Kubernetes 与单 Dispatcher 多引擎提交验证记录

日期：2026-09-26～27。目标环境：102 同一个 Dispatcher 管理 Local Docker 与 103 Kubernetes。
本文按时间保留分阶段结果；前半部分为 Backend 探针，文末为实际 Admin → Kafka → Dispatcher → Runner 验证，不把两者混为一谈。

## 本轮修复

| 问题 | 调整 | 验证 |
| --- | --- | --- |
| Secret 先创建再添加标签，依赖未声明的 patch 权限；中断时可能留下无身份标签的凭据 | 数据与完整执行身份标签通过一次 create 创建；临时文档完成后删除 | 单元测试通过；103 限权账号无 patch 权限，实际创建、读取、删除成功 |
| 替换和清理 Secret 未核对完整身份 | 名称、managed、engine、execution、run、attempt 全部校验；不匹配拒绝，NotFound 幂等 | 身份冲突与缺失测试通过 |
| 额外 Spark Conf 可以覆盖固定的集群提交参数 | 额外配置在前，平台固定目标和身份配置在后 | 固定参数优先测试通过 |
| 提交和 kubectl 可能使用不同的默认集群/凭据 | 显式 kubeconfig 通过独立子进程环境传入；kubectl API 地址与 master 一致；缺失显式文件时 readiness 失败 | 子进程并发环境隔离、显式 kubeconfig 传递及缺失检查通过 |
| readiness 未检查 Kubernetes 服务端最低版本 | 读取实际 `/version` 并要求至少 1.32；版本响应无法确认则不就绪 | 1.30/1.31 拒绝、1.32/1.33+ 通过、未知版本拒绝；103 限权账号实际可读该接口 |
| Spark 4.1.1 对 Service/ConfigMap 使用 server-side apply，提交实际报 403 | RBAC 与 readiness 补这两类资源的 patch；不扩大 Secret 权限 | 同一限权账号重跑 SparkPi 成功 |
| 无 PVC 任务结束时仍默认删除 PVC，实际报 403 | 当前 emptyDir 模式固定关闭 Driver PVC 所有权/复用 | SparkPi 重跑退出 0，Driver 日志无 ERROR/Exception/Forbidden |
| 已结束 Driver/ConfigMap 没有自动回收 | 归档后确认终态与完整身份，再删除 Driver/残留 Executor/Secret；选择器增加 runId/attempt | 新增 3 项单元测试；实际 Backend 对成功 Driver 完成恢复、日志读取、清理及重复清理 |
| PostgreSQL 批写失败日志包含绑定值/失败行 | RuntimeJdbcConnection 固定关闭 pgjdbc logServerErrorDetail，覆盖 URL 同名参数 | 2 项单元测试通过；r2 镜像真实约束失败回归通过，Driver 日志不再出现合成行值，SQLState 与诊断 ID 保留 |

上述第一阶段未改变 Runner 协议、任务定义、原引擎注册或业务数据，当时尚未部署到 102。后续同进程升级见文末。

## 自动化验证

使用根目录 Maven Wrapper、现有 settings 与 Java 21。没有绕过工程构建配置。

```powershell
$env:JAVA_HOME='F:\language\java\jdk\jdk-21.0.2'
$env:DATASCALPEL_KAFKA_INTEGRATION='true'
$env:DATASCALPEL_KAFKA_BOOTSTRAP_SERVERS='192.168.5.102:9092'
.\mvnw.cmd -pl data-scalpel-task-dispatcher -am '-Dtest=*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

- Dispatcher 全量执行 **55 项，失败 0、错误 0、跳过 0**，包括连接 102 Kafka 的 Topic 集成测试。
- 随后新增显式 kubeconfig 传递、缺失配置不回退两项测试；针对 `KubernetesSecretLifecycleTest` 再执行 **5 项，全部通过**（其中 3 项为重跑，不与上述 55 简单相加）。
- 新增上述两项及五个最低版本用例后，18:08 再跑完整 Dispatcher 套件：**62 项，失败 0、错误 0、跳过 0**，实际 Kafka 集成再次通过。
- 补充 RBAC 校验后，18:43 完整 Dispatcher 套件 **63 项全部通过**。增加终态清理后，18:53 Kubernetes 针对性套件 **20 项全部通过**，不与前述次数简单相加。
- 19:11 包含全部清理改动的完整 Dispatcher 套件 **66 项全部通过，失败/错误/跳过均为 0**，Kafka 集成实际连接 102。
- 19:02 `RuntimeJdbcConnectionTest` **2 项全部通过**，其中直接调用所用 pgjdbc 的 URL 解析器验证 URL 重复参数优先级。
- 全量回归中修正了旧测试夹具：注册请求补齐当前必填 resourcePolicy；状态观测第二次调用明确推进到到期时间；进程输出测试不再依赖 Windows 不具备的 sh/printf。未为测试放宽生产状态机。
- Kafka Topic 测试覆盖 command、runner event、admin event、runner control 四类临时 Topic 的创建和查询，finally 中删除全部四类，补上旧测试遗漏的 runner control 清理。
- 大部分 Dispatcher 集成测试使用隔离 H2 和模拟执行后端；它们不能替代真实 Docker/Kubernetes 应用执行。

## 制品检查

```powershell
.\mvnw.cmd -pl data-scalpel-task-engine -am -Ptask-engine-full-package '-DskipTests' package
```

完整打包成功。第一轮 cluster Runner JAR 为 813,309,956 字节；`RunnerClusterArtifactVerifier` 实际执行退出码 0。该步骤跳过单元测试，不表述为 Engine 全量测试通过。制品检查进程出现 SLF4J 无绑定警告，但后续真实 Spark Driver 正常输出 Spark/Runner 日志；不将独立检查进程的警告等同于 Driver 无日志。

初次 Spark 基础镜像下载超时；重试后成功，实测基础镜像为 Spark 4.1.1、Scala 2.13.17、Java 21.0.9。已在 102 构建专用 Runner 镜像，在 103 containerd 导入并按实际 digest 运行 Driver/Executor。

- 基础镜像 manifest digest：`sha256:0b8f5befe00d258df03b252158b3894b8654390f1958741accf98155539a662a`。
- cluster JAR SHA256：`22bcc64d2413ac0a9d0427862bfda146575c983d4fbc83c8a4d4cc7ffa25bfe9`，上传前后相同。
- 构建镜像本地 ID：`sha256:23b74ed4ad487b8ae00f85be2328cd315dfb701fcb7cc2633a7717b821d71392`，这不是部署时使用的仓库 manifest digest。
- 第一轮实际运行的 OCI manifest digest：`sha256:7eb4e341855e845f9d3b9674eaf0d56d74ac8165bebf50de8162d9cf77e06187`；导入归档 SHA256 为 `8cf0a3965bece1f284973c99b07f0cc1924a035a62d29025460c0b9e3f0bdedb`，102/103 相同。
- 可复用构建文件见 [Runner 镜像](../../deploy/kubernetes/README.md)。

19:08 完成包含 PostgreSQL 日志修复的 r2 全量打包，19:13 导入 103；成功、失败回归均使用这一版本：

- cluster JAR：813,310,568 字节，SHA256 `8d7ed4453111074c6bfe3d642e8e6a2516ef8df502c44360f5c2195f961ea90a`，本机/102 一致。
- 当前 Runner 引用：`docker.io/library/datascalpel-spark-runner@sha256:a8df2aedd6e361fac400c5433b04d4ff9a8a07b3e3a11ff6a844a7e9086f3fa5`。
- 传输归档 SHA256：`7cc07348a335c0fe5912da472d1896e4e8a628e98b57485e14b2cf6568072f45`，102/103 一致；导入后归档已删除以回收空间。
- 镜像目前仅在单节点预加载，并非可供其他节点拉取的内部仓库镜像。迁入公司集群前须推送受控仓库并重新核对仓库摘要。

## 真实环境检查和清理

- 103：限权 ServiceAccount `can-i patch secrets` 返回 no，但一次 create 可完成完整标签 Secret 创建；读取核对后删除了测试 Secret。
- 103 初始检查时无 Pod/Secret；后续真实任务均使用独立测试身份、Topic、对象 Key 和临时 Schema。升级与镜像导入后根分区约 12 GiB 已用、36 GiB 可用（含传输归档），不是最终清理后数值。
- 用户确认临时兼容组合后，备份集群配置与 etcd 快照，并复制到本机受限凭据目录。安装 ELRepo `5.4.278-1.el7.elrepo` 内核，RPM DSA/SHA256 签名核验通过，保留旧 3.10 内核。新内核仅用于本临时环境，EL7 的 ELRepo 包也已停止更新，不作为生产版本建议。
- 保留部署配置、受限凭据和必要运维记录；不将令牌、密码、launch.json 或完整集群凭据提交 Git。

## 真实执行事实

以下由测试探针生成 Manifest/Launch 并调用 Spark 或现有 Backend 类，不经过 Admin 业务注册和 Dispatcher 持久化调度；因此不能等同于完整平台端到端测试。

| 场景 | 实测 |
| --- | --- |
| SparkPi 基础执行 | 18:41 从 102 限权客户端提交至 103；一个 Driver、一个 Executor，10 个 partition；退出 0，正常清理，无权限错误 |
| Canvas JDBC 成功 | executionId `c8713276-5608-4d32-9e5b-e376ded15065`；102 临时 PG 表 → 103 Runner/Executor → 102 临时 PG 目标；1,000 行，min/max 为 1/1000，sum(id)=500500；两个节点 SUCCESS，affectedRows=1000 |
| 成功结果交付 | 私有 MinIO 收到合法 result.json；独立 Kafka Topic 收到 RUNNER_STARTED、RUNNER_RESULT_AVAILABLE，执行身份一致 |
| Canvas JDBC 约束失败 | executionId `6286b774-e9c1-46dc-a7c9-4c3355c4d0c3`；目标 CHECK 拒绝写入，SQLState=23514，JDBC_CONSTRAINT_VIOLATION，WRITE/JDBC_OUTPUT，retryable=false；目标 0 行；错误结果与事件送达 |
| Backend 真实定位/清理 | 新建 Backend 对象按完整标签恢复上述成功 Driver；inspect=SUCCEEDED，读取 27,283 字节日志；清理调用两次均通过，之后无法再恢复该 Pod。不是 Dispatcher 进程重启恢复测试 |
| 活动 Spark 取消 | executionId `759154f9-45d3-4809-8f94-9ffa214b2c94`；Backend 恢复时明确为 RUNNING，再调用 cancel，inspect 变为 UNKNOWN（Pod 已删除），重复 cleanup 成功。该取消探针运行 SparkPi，不是 Admin 发起的 Runner 取消；初次短任务在取消前已完成，不计入活动取消通过 |
| 探针自身问题 | 首次 Manifest 缺少 autoIncrement/generated，严格解析拒绝；补全探针字段后重跑成功。未放宽生产 JSON 校验；该次临时资源已清理 |
| r2 日志修复失败回归 | executionId `fbabc10b-86f4-4dae-866d-6fb737c447d0`；CHECK 失败，目标 0 行，SQLState=23514，诊断 ID `e2a5097f-f21a-44c7-913a-5c50f2f716c2`；结果/事件送达，日志无测试行值、密码或签名 URL；批次摘要为 `<unknown>`，未丢失错误分类 |
| r2 成功回归 | executionId `3d78ac9a-463b-4067-af3a-974a4274f290`；Pod Succeeded，两个节点 SUCCESS，affectedRows=1000；目标 1000 行，min/max=1/1000，sum(id)=500500；MinIO 结果与 Kafka 两类事件身份一致，日志无 ERROR |
| Backend 就绪检查 | 从 102 临时客户端调用实际 Kubernetes Backend，返回 `ready=true, issues=[]`；覆盖当前版本、CLI、受限 RBAC 等后端检查；制品端口在该探针中为替身，不能当作 Dispatcher 公共依赖完整就绪验证 |

失败测试发现 Spark/pgjdbc 原始日志带合成数据值，不能仅凭 Runner 顶层安全错误断言日志已脱敏。连接层修复已通过 r2 镜像实际回归；未声称其他驱动或用户自建连接均满足此项。

### 本轮清理与环境复查

19:20 左右完成本轮全部探针资源清理：按确切执行身份删除临时 PG 表及空 Schema、独立 Kafka Topic、MinIO 对象 Key、Driver 和 launch Secret。`datascalpel-test` 无测试 Pod/Secret，只保留系统 `kube-root-ca.crt` ConfigMap；未删除命名空间、ServiceAccount 或 RBAC。

同时删除 102/103 的两轮镜像传输归档、未使用的 r1 Runner 镜像引用、102 临时 Backend 探针源码/状态文件，以及 103 未安装的备用 kernel-uek RPM；保留当前 r2 镜像、构建输入、配置、受限 kubeconfig、升级前 etcd/配置备份和文档。未执行全局镜像 prune，旧共享层由运行时按引用回收。删除的合成数据/传输包不提供撤销，但可以重新生成；没有清理用户业务表或现有执行历史。

清理后单次观测：103 根分区约 12 GiB 已用、36 GiB 可用；102 约 32 GiB 已用、25 GiB 可用。103 节点 Ready，KubeSphere 三个核心组件均 1/1 Running。102 原 Dispatcher 启动时间仍为 10:33，`192.168.5.102:18092/actuator/health` 返回 UP；本轮未重启它，也未将新 Dispatcher 包替换上线。

集群详细安装与访问见 [103 环境记录](../operations/k8s103-single-node-test.md)。

## 第一阶段结束时的未完成项（后续结果见下节）

Spark 版本阻断已解除：103 已重启进入 5.4 内核，Kubernetes 经 1.31 逐级升级到 1.32.13，基础组件 Ready。用户接受保留 KubeSphere 4.1.3 做矩阵外临时兼容验证；不能将该组合表述为官方兼容或生产可用。升级后控制台认证待当前密码（初始密码已被用户修改，未重置）。

参考：[Spark 4.1.1 部署前提](https://spark.apache.org/docs/4.1.1/running-on-kubernetes.html#prerequisites)、[KubeSphere 4.1 支持范围](https://www.kubesphere.io/docs/v4.1/03-installation-and-upgrade/01-preparations/01-supported-k8s/)。

仍需完成：

1. 完成升级后 KubeSphere 当前密码登录验证，保留矩阵外兼容限制。
2. 已完成本轮新镜像日志修复回归、Backend 活动 Spark 取消及资源清理；平台发起的 Runner 取消仍属下述端到端待验收范围。
3. 实施并验证 [多目标 Dispatcher 方案](../design/task-execution-platform/11-multi-engine-dispatcher.md)；当前代码仍为单实例单后端，尚无“同一地址发现多个目标并勾选注册”的完整能力。
4. 完成 Admin 注册、真实任务成功/失败/取消、日志和结果回传、重启恢复、引擎隔离、删除保护与资源清理。
5. 回归 102 原 Local Docker 引擎，记录前后结果及剩余边界。

在上述真实链路完成前，本轮结论只是“发现并修复部分 Kubernetes 后端缺陷，基础回归通过”，不是“K8s 提交验证完成”。

## 第二阶段：单进程多目标与完整平台链路

2026-09-26 23:00 后在维护窗口将 102 原容器升级为多目标 Dispatcher，没有新增第二个 Dispatcher 进程。
保留实例 `6838f185-d944-45ab-a9b4-712a1e371b8b`、原 Docker 引擎 `63a37530-db75-46ad-98d1-c9dba98fbf3d` 和原 11 条执行历史。
使用 PG17 `pg_dump` 备份，回填目标键、物理指纹；旧 Kafka 消费组保持原值，未重置 Offset。

实际浏览器在“连接 Dispatcher”中输入 102 地址及 Token，发现两个目标；原 Docker 不重复创建，勾选 K8s 后新增引擎
`b70424bf-8952-433d-b128-f6ba8a49e5bd`。两条记录使用同一 URL，不需要用户填写 kubeconfig、Namespace、镜像或目标键。
新 K8s 配置为 1 个在途应用、1 个提交槽位、1 Core/1 GiB Driver、1 个 1 Core/1 GiB Executor；这是单节点小规模验证配置，不是生产容量承诺。

### 实测结果

| 场景 | 事实与结果 |
| --- | --- |
| 真正的 Kafka 执行链路 | 任务通过 Admin 正常创建、保存、发布、运行；Admin Outbox 发 command Topic，Dispatcher 按引擎监听，Runner 事件经独占 Topic 回传，再经共享 admin-event Topic 同步 Admin；未用 HTTP 替代执行消息 |
| Docker 成功 | `d967cf7e-9f85-44b5-bf11-86677234c92f`、`0efed5a3-faab-4979-9334-cba5643af7e2` 各 SUCCESS/1000 行；目标合计 2000 行，1000 个不同 ID，sum(id)=1001000 |
| K8s 初次失败与修正 | `bac2004b-c185-402a-889b-93eab54c01a8` 在下载 Manifest 时不能解析 `host.docker.internal`。将 Dispatcher 的 Runner 文件地址改为 `http://192.168.5.102:9000`，不是修改业务数据库或增加 hosts 绕过 |
| K8s 修正后成功 | `33c021de-ffd0-4e13-9742-d20f6e8ce701`，Driver `ds-61a608dd8a5b4594b347ac4e32719f84-driver`，Admin SUCCESS/1000 行；目标 1000 行、1000 不同 ID、范围 1～1000、sum(id)=500500 |
| 约束失败 | Docker `b6f25238-682d-47e4-a405-d94c04269e60`、K8s `bc996800-46ba-4252-a3dc-8b22ebc9591e` 均 FAILED，回传数据库约束错误、affectedRows=0；两个目标均 0 行 |
| 日志/结果制品 | 成功和约束失败的 Admin artifacts 接口均返回 result/log AVAILABLE，可读取实际大小；初次文件网络失败没有生成结果，只归档日志，未伪造结果 |
| 慢任务夹具 | 最初 `pg_sleep(90)` 被 JDBC 默认 15 秒 socketTimeout 拦截，两端均正确报告超时；不算取消通过。改为逐行短延时、持续输出的视图后重测，未修改生产连接超时 |
| 运行中重启 | Docker `899a1618-7582-4f75-90f4-1d3c5235fb4d`、K8s `890a7eae-9ba4-4933-a922-32c5c60eab31` 同时 RUNNING 时重启唯一 Dispatcher；重启后两个 Run 的外部应用 ID 不变，仍为 RUNNING |
| 运行中取消 | 上述两个 Run 经 Admin 发取消命令，均从 CANCEL_REQUESTED 收敛到 CANCELLED；未把命令发送成功当作取消完成 |
| 引擎独立 Drain | 只 Drain K8s，K8s 新运行返回 409；Docker `5ce48465-d1fd-43af-af72-7e320fe24b6b` 仍 SUCCESS/1000 行 |
| 在途额度/排队取消 | K8s 在途上限 1，有慢任务运行时第二个 Run `c38f7144-9152-4749-9e5a-0a561833e72b` 保持 QUEUED 且没有外部应用；Drain 后仍可取消队列与活动任务，两者均 CANCELLED |
| 生命周期与删除保护 | 无活动/待清理执行后可安全反注册至 INACTIVE；仍被任务引用时删除返回 409；随后重新激活为 ACTIVE，Docker 不受影响 |
| 登记保护 | 相同目标重复登记不新增记录；同批两项已存在目标成功、一项不存在目标失败；旧指纹、旧实例身份、错误 Token 被拒绝；前后引擎 ID 集合和独占 Topic 保持不变 |
| Pod/凭据清理 | 取消与失败收敛后，103 `datascalpel-test` 无 Pod/Secret/Service，只保留系统 kube-root-ca ConfigMap；当前节点根分区 12 GiB 已用、36 GiB 可用 |

### 代码验证与优化

- 新增多目标 Dispatcher 集成覆盖引擎队列/Drain 隔离、强制反注册范围、目标占用、Topic 冲突、旧指纹拒绝；本阶段针对性 Dispatcher 30 项通过，不与第一阶段套件数量相加。
- Admin 新增发现/批量登记集成 4 项通过；使用隔离 H2、模拟 Dispatcher HTTP 客户端，不能代替上面的真实环境测试。全量 Admin 历史测试存在构造参数过期的编译问题，未宣称全量通过；临时定向编译过滤已撤除。
- 前端发现抽屉 3 项通过，覆盖只读发现、连接变更失效、部分成功反馈、提交时锁定策略字段。TS 类型检查和计算引擎目录 ESLint 已通过。
- 实际 `/v3/api-docs` 核对新接口中文摘要、请求、目标目录与能力引用字段；补齐 targets 的显式 min=1，避免文档生成器给出 minItems=0。
- 统一启动脚本修正 classpath 探针的 exec 参数污染 SDK 编译调用，以及应用进程误用 PATH Java17 的问题；仍使用项目 Wrapper/settings，应用统一按 JAVA_HOME 使用 Java21。
- 文件下载/上传的连接异常补上既有 `MANIFEST_DOWNLOAD_FAILED` / `RESULT_UPLOAD_FAILED` 包装，避免被错误归类为 JDBC_CONNECTION_FAILED；新增拒绝连接回归 1 项通过，未变更协议或新增错误码。

### r3 上线回归（2026-09-27 00:26）

102 Local Docker 更新为 r3 local Runner，103 导入 r3 cluster 镜像并按 digest 固定；未改变实例、引擎和目标身份。

| 后端 / 场景 | Admin Run ID | 结果 | Result / Log 字节数 |
| --- | --- | --- | --- |
| Docker 成功 | `59f75ef8-0be7-4e54-b97c-1d36fb4b0c1b` | SUCCESS，1000 行 | 1655 / 16814 |
| K8s 成功 | `a795a7a9-0904-48ab-b017-930b61cd13b3` | SUCCESS，1000 行 | 1656 / 30566 |
| Docker 约束失败 | `1467831c-6bb7-4060-bbb2-69fc3e07767c` | FAILED，0 行，数据库约束错误 | 2488 / 62208 |
| K8s 约束失败 | `6443dadf-b6d0-416f-89ab-f07f9cefc2d2` | FAILED，0 行，数据库约束错误 | 2487 / 74024 |

四次运行均由 Admin 触发、经 Kafka 调度；结果与日志均 AVAILABLE。成功表累积分别为 Docker 4000 行、K8s 2000 行，各有 1000 个不同 ID、范围 1～1000，sum(id) 分别为 2002000 / 1001000；两个约束失败表与取消表均为 0 行。
前端 TS/计算引擎 ESLint 再次通过，浏览器实测显示同地址的两条已激活/正常引擎及各自消息通道，“添加同实例的其他目标”正确预填地址。
本地通过统一脚本恢复 Admin/TaskEngine/UI；运行时 OpenAPI 已确认 `targets.minItems=1, maxItems=32`。

### 最终清理与交付状态（2026-09-27）

- 逐项核对本轮 6 个测试任务及 17 个 Run UUID，确认全部终态、外部清理完成后，删除这些任务、1 个临时数据源、7 张合成表、1 个慢查询视图及临时 Schema `ds_multi_e2e_ec05c269f7`。
- 删除这 17 次测试对应的终态运行、队列、消息、告警记录，以及精确对象前缀下的 45 个 MinIO 对象；未删除共享 Kafka Topic，也未重置消费组/Offset。执行事实和关键 ID 留在本文，不在仓库保留原始 JSON 证据或一次性探针脚本。
- 102 Dispatcher 账本仍保留原来的 11 条历史：SUCCESS 9、FAILED 1、STOPPED 1；两条引擎登记均 ACTIVE，目标发现刷新后均 ready=true。
- 删除 102/103 各约 1.5 GiB 的 r3 镜像传输归档；保留已安装 r3、必要构建输入、旧制品回退参考和受限备份。未进行全局镜像清理或删除用户业务数据。合成数据与传输包删除不可撤销，可重新生成。
- 清理后 102 根分区约 34 GiB 已用、24 GiB 可用；103 约 13 GiB 已用、35 GiB 可用。103 节点 Ready，任务命名空间无 Pod/Secret/Service，KubeSphere 3 个核心组件均 1/1 Running。
- 清理完成时本地 Admin 8080、TaskEngine 8091、UI 8887 可用；后续进程归属见下方补充回归。实际浏览器已检查计算引擎列表及同实例追加入口。代码尚未 Git 提交。

### 补充回归（2026-09-27）

- 注册部分失败后的自动重新发现会覆盖失败项的名称和容量策略。新增回归先复现：`my-docker-engine` 被恢复成 `local`；随后修复为只对同实例、同物理目标保留尚未登记的草稿。实例/物理指纹改变时清除旧目标草稿，已有引擎仍使用服务端配置。
- 发现抽屉 6 项测试通过，包括失败重试配置保留、身份变化失效、提交期间字段锁定及部分成功提示；前端类型检查和计算引擎 ESLint 通过。
- 整个计算引擎前端模块 4 个测试文件、22 项重跑全部通过（187.54 秒）。首次运行与本地应用启动重叠，20 项通过、2 项旧创建表单测试在 30 秒超时；未调整测试代码或超时阈值，同命令重跑通过。仅记录为一次超时，不能据此认定资源竞争是唯一原因。
- Dispatcher 全套 75 项通过，0 失败、0 错误、0 跳过，包含真实 102 Kafka 临时 Topic 的创建/检查/删除。该测试只操作随机测试 Topic，不清理已有引擎通道。
- 新增调度器测试证明：一个提交调用阻塞时，另一引擎仍获得提交机会，观测/清理通道继续工作；不可用目标不领取队列，健康目标继续。新增 Kafka 接收器测试证明：即使载荷、Header 和 Key 自洽，消息进入另一引擎所属 Listener 时也被拒绝，不调用执行状态服务。上述四项单独复跑通过；这些是受控测试，不代替真实外部集群故障注入。
- 本地恢复期间用户从 IDEA 启动 Admin / Task Engine，统一脚本中的重复 Admin 因 8080 占用未启动成功。核实归属后仅结束本轮失败启动器，保留 IDEA 进程和现有 UI。Admin 健康检查 UP；真实接口再次发现两个 ready=true 目标，浏览器显示同一 Dispatcher 地址下 Docker / K8s 两条“已激活 / 正常”记录及独立命令、Runner Topic。

### 验证边界

本轮是单节点、少量并发的真实功能/恢复验证，不是长时间容量压测或生产认证。没有真实 YARN 集群、双 K8s 集群、跨节点故障、公共 Kafka/DB 故障恢复和 K8s 持久化流任务验收。
K8s 目标未配置共享持久化 Checkpoint，故不宣称支持流式任务。单目标隔离不隔离整个 Dispatcher JVM 和公共基础设施故障。
KubeSphere 当前密码认证结果已补充在下节；三个核心组件运行不等于浏览器页面完整验收。

## 第三阶段：Topic 展示、管理操作与当前账号回归（2026-09-27）

### 页面与行为调整

- Topic 不需要用户输入。准确来源为 Admin 首次登记时自动分配独占 command / runner-event，admin-event 取系统监听配置，再注册到 Dispatcher；并不是从发现目录直接领取三个预设通道。旧通道继续保留。
- 列表增加“查看全部 Topic”，详情、修改抽屉和发现结果中的已注册项展示完整只读值并支持复制。
- 操作统一改为“暂停任务调度 / 恢复任务调度 / 停用引擎（需无任务） / 取消全部任务并停用”，每项确认说明队列、运行任务、资源清理及不可回滚的数据写入影响。
- 新增真正的 `resume` 管理端点；恢复原队列，不通过重新注册替代恢复，不改 Topic 或重启已有应用。
- 修改配置的提示明确：队列会暂停，不能只等待队列自动结束；需恢复调度处理或手动取消，再次应用配置。

### 本轮捕获的真实缺陷

| 复现 | 修复 |
| --- | --- |
| 有排队/运行任务时停用，Dispatcher 返回 409，Admin 却包装成 502 并标记健康 DOWN | Admin 保留业务 409，使用本地安全提示，不回显远端正文、不因业务拒绝改变健康状态 |
| 强制停用：Dispatcher 默认等待 30 秒，Admin 默认 HTTP 10 秒、前端 20 秒，先于清理窗口超时 | 仅强制停用的 Admin 读取至少等待 60 秒，页面等待 90 秒；普通请求保留短超时。超时仍不能当作任务已经取消 |
| 强制停用时队列在 Dispatcher 已 CANCELLED，Admin 仍 QUEUED | 在取消排队账本的同一事务中补入 EXECUTION_CANCELLED Outbox，重复停用不重复发事件 |

漏发事件的修复前样本为 `d121b04b-4914-4952-a7be-9b55459d9faf`，Dispatcher 无外部应用而已取消，Admin 未收到新序号终态。该样本不能计作取消通过。

### 自动化与部署

- Dispatcher 最终全套 **76 项通过，0 失败/错误/跳过**，包括真实 102 Kafka 临时 Topic 测试；多目标测试增加排队取消事件、其他引擎无事件及重复停用不重复通知断言。
- Admin 发现/生命周期定向 **6 项通过**，覆盖恢复及远端 409 安全映射；原有全量 Admin 测试仍存在过期构造参数编译问题，不宣称全部 Admin 测试通过。定向临时编译过滤已撤除。
- Dispatcher HTTP 客户端 **2 项通过**，新增强制停用允许超过普通短请求超时的回归。
- 前端计算引擎模块先完整 **24 项通过**；随后改进配置提示，抽屉 **9 项复跑通过**，新增强制停用等待策略 **2 项通过**。以上复跑次数不重复相加。TypeScript build 检查与计算引擎 ESLint 通过。
- Admin / Dispatcher package 成功。102 最终部署 `releases/20260927-lifecycle/dispatcher.jar`，SHA256 `8ca9d91d92df353e7d3973beaa6f80bf614cddfa5c835bd9be77b8ffca1c15f1`；同一容器健康正常，未改 Runner r3、实例或 Topic。
- 统一启动脚本补充 `--frontend-only`，可复用 IDEA 的 Admin；校验 Bash 语法、与 prepare 冲突拒绝、8887 已占用时严格拒绝，未换端口另起环境。当前已有前端返回 200，不重复启动。

### KubeSphere 当前密码验证

用户提供的当前密码实际通过 `admin` 登录，`/login` 返回 success=true；带认证会话查询用户、节点、`datascalpel-test` Pod 列表均返回 HTTP 200/JSON，节点 Ready。未重置密码，未向仓库或原密码文件写入新密码。
本轮浏览器自动化连接报 `nodeRepl.fetch request failed`，未取得页面状态，因此上述属于真实 HTTP 会话验证，不能表述为已完成浏览器点击及视觉验收。

### 本轮真实执行结果

| 场景 | 运行 ID / 事实 |
| --- | --- |
| Docker 成功 | `3e501661-a29e-4b85-a25e-ad64c9c4b88c`，SUCCESS / 1000 行 |
| K8s 成功 | `3616155c-5c44-4851-9681-4eda00e6d097`，SUCCESS / 1000 行 |
| Docker / K8s 约束失败 | `8982e0fa-acb9-477f-bc63-93f1d2a1f7af` / `4ea739e6-a298-4ca5-88e7-10a10e56314f`，均 FAILED / 0 行；实际失败目标表也均为 0 行 |
| 暂停后恢复原队列 | K8s 慢任务 `95486569-7440-41ed-96b0-25574366a356` 运行中，队列 `1ffe8826-7152-4ae7-bfe6-eb25e116a06c` 保持 QUEUED 且无外部应用；暂停拒绝新准入、原运行应用 ID 不变；恢复 ACTIVE 后 Topic 不变，重复恢复返回 409；经 Kafka 取消慢任务后，原队列 SUCCESS / 1000 行 |
| 另一引擎不受暂停影响 | Docker `12efe130-c743-4fc1-beb6-d67d3e54272b` 在 K8s 暂停时仍可提交，最终 SUCCESS / 1000 行 |
| 修复后强制停用 | 运行任务 `58a29aff-041e-44bc-8e12-d52a21bd340f`、排队任务 `6f37e420-f9fd-46a3-a45b-e0571cb66d04` 均 CANCELLED；后者无外部应用，Dispatcher 一条 EXECUTION_CANCELLED Outbox 已 PUBLISHED，Admin Inbox 收到同 messageId，序号 2 |
| 恢复登记 | 清理后安全停用至 INACTIVE；任务引用阻止删除（409）；再次登记为 ACTIVE。一轮立即登记遇到 readiness 缓存更新中而拒绝，重新发现确认 ready=true 后成功，未绕过就绪校验 |

结果/日志均 AVAILABLE：Docker 恢复期间成功样本为 1655 / 16812 字节，K8s 恢复队列成功样本为 1654 / 27696 字节；Docker/K8s 约束失败分别 2487 / 62207、2486 / 76897 字节。清理前成功表累计 Docker 2000 行、K8s 3000 行，各 1000 个不同 ID，sum(id) 分别 1001000 / 1501500；失败和取消目标均为 0 行。

**尚未完成的现场验证：** 用户负责 IDEA 重启，当前 Admin 仍是旧进程。停用拒绝现场仍返回 502、强制停用首次请求仍在 10 秒超时；前述后端修复已通过编译和针对性测试，但须重启后复验 409 与延长等待窗口。强制停用回归通过随后查询/安全停用确认远端实际 INACTIVE，不能把该轮首次 HTTP 请求标为成功。浏览器自动化连接未恢复，新增页面交互仍待实际点击验收；不将组件测试替代这一项。

清理过程中发现：Pod 已删除后，PG 仍残留一个对本轮 `probe_slow` 的只读 SELECT 会话，等待 `ClientWrite`，阻塞 DROP VIEW。核对确切 PID、SQL、测试 Schema 及无外部 Pod 后仅终止该测试连接，未终止用户连接。任务外部应用终态和 Pod 清理不保证远端数据库 TCP 会话立即消失；本轮不据此宣称外部数据库活动全部自动清理，也不把已写入数据当作取消后回滚。这是强制终止的运维边界，迁入公司环境应结合数据库连接存活/语句超时策略再验证。

### 本阶段清理与保留

- 已删除本轮 7 个合成测试任务、1 个数据源、7 张表、1 个视图及 Schema `ds_multi_e2e_6f86210e79`，以及精确核对的 13 条运行/队列/消息/告警记录、31 个 MinIO 对象。缺失事件的旧样本先确认 Dispatcher 已 CANCELLED 且无外部应用才清理，未伪造成功终态。
- 删除的测试数据和制品不能撤销，可重新生成；用户业务数据、原 11 条 Dispatcher 历史（9 SUCCESS / 1 FAILED / 1 STOPPED）、两个 ACTIVE 引擎及共享 Kafka Topic 均保留。未重置消费组或 Offset。
- 102 容器 healthy，根分区约 23 GiB 可用；103 节点 Ready，任务命名空间无 Pod/Secret/Service，根分区约 35 GiB 可用。目标就绪异步刷新完成后两个目标均 ready=true。
- 本地 8080 Admin、8091 Task Engine、8887 前端均保留，未停止用户 IDEA 进程。新增一次性联调探针未放入工程或 Git；保留可重复执行的正式回归测试与本文事实记录。未 Git 提交。
