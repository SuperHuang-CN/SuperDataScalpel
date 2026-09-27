# 11 Dispatcher 单实例多计算引擎改造方案

状态：多目标基础能力已实施；2026-09-27 实例共享 Topic 与两文件配置改造进行中，本轮部署/验收以验证记录为准。

已确认的设计细节：每条计算引擎记录只绑定一个后端类型和一个具体执行目标；目标连接细节由
Dispatcher 部署配置维护；Admin 选择目标键并主动发起注册。其余评审项见第 12 节。

本文记录已确认并落实的多目标设计。102 单进程已登记 Local Docker 与 103 Kubernetes 两个目标；
实测结果及未覆盖范围见[验证记录](../../verification/kubernetes-submission-20260926.md)。
代码具备 YARN 目标配置不等于已完成真实 YARN 集群验收。

## 1. 结论与目标

建议支持“一套 Dispatcher 程序、一个进程管理多个计算引擎”，同时保留按网络、权限和
故障域拆成多个 Dispatcher 实例部署的能力。不强制每一种提交后端单独部署进程。

核心规则：

1. 启动配置明确声明执行目标及是否启用，只初始化已启用目标的客户端和调度能力。
2. Docker、YARN、Kubernetes 是三种后端类型，不是三个固定的计算引擎。
3. 同一类型可以配置多个目标，例如 `k8s-a`、`k8s-b`；每个目标独立注册为计算引擎。
4. 任务明确选择计算引擎，Dispatcher 按 `engineId` 路由，不按任务类型猜测提交位置。
5. 每个引擎独立准入、排队、并发、健康和生命周期；实例设置共享资源上限。
6. 同一个引擎同一时刻仍只有一个 Dispatcher 所有者，本次不建设多实例抢占和自动接管。

不新增 Maven 模块、微服务、服务发现、动态插件框架或另一套执行协议。

## 2. 改造前后对照

| 项目 | 改造前 | 当前实现 |
| --- | --- | --- |
| 后端启用 | `data-scalpel.dispatcher.backend` 单值，条件装配一个 Backend | 按配置目标装配多个 Backend 实例 |
| 计算引擎注册 | Dispatcher 只维护一个当前注册 | 按 `engineId` 维护多个独立注册 |
| 平台选择 | 任务绑定 `computeEngineId` | 保留，不增加按任务类型自动选址 |
| Backend 调用 | Coordinator 注入单个 `TaskExecutionBackend` | 从执行记录的目标绑定定位对应 Backend |
| 队列 | 一套执行账本，但按单注册假设准入 | 共用账本、按引擎分组独立调度 |
| 生命周期 | 注册、Drain、反注册影响整个实例当前注册 | 引擎操作只影响指定引擎 |
| 健康 | 单 Backend 健康参与整体就绪 | 公共基础设施与各引擎健康分开报告 |
| 部署 | 同一程序包通过配置选择 Docker/YARN/K8s | 同一程序包可运行一个或多个目标；仍允许拆实例 |

对应实现入口（以下单注册描述仅指改造前）：

- [注册服务](../../../data-scalpel-task-dispatcher/src/main/java/cn/superhuang/data/scalpel/dispatcher/management/DispatcherRegistrationService.java)：读取首条注册，拒绝绑定其他活动引擎。
- [执行协调器](../../../data-scalpel-task-dispatcher/src/main/java/cn/superhuang/data/scalpel/dispatcher/service/DispatcherExecutionCoordinator.java)：使用单个 Backend 提交、观测、取消和清理。
- [执行状态服务](../../../data-scalpel-task-dispatcher/src/main/java/cn/superhuang/data/scalpel/dispatcher/service/DispatcherExecutionStateService.java)：锁定注册集合后只使用首个注册准入。
- [计算引擎管理](01-compute-engine-management.md)：当前采用一实例一后端，任务显式绑定引擎。

## 3. 概念与身份

| 概念 | 标识 | 职责 |
| --- | --- | --- |
| Dispatcher 实例 | `dispatcherInstanceId` | 一个控制进程及其执行账本，接入 Kafka、制品存储和外部提交系统 |
| 部署执行目标 | `targetKey` | Dispatcher 配置中的稳定目标键，关联后端类型及连接配置 |
| 平台计算引擎 | `engineId` | Admin 中用户选择的执行位置，管理名称、容量策略和注册状态 |
| 后端类型 | `backendType` | `LOCAL_DOCKER`、`YARN`、`KUBERNETES` 的提交实现 |
| 单次执行 | 现有 `executionId/attempt` | 固定其引擎、目标、提交资源与外部执行身份 |

采用一对一绑定：同一 Dispatcher 实例中的一个 `targetKey` 同时只能绑定一个活动 `engineId`；
一个 `engineId` 同时只能绑定一个 Dispatcher 目标，且只对应一个后端类型。
不同引擎可以共享 Dispatcher URL 和实例 ID，不能仅靠后端类型区分目标。

绑定关系为：`engineId → (dispatcherInstanceId, targetKey) → 具体执行环境`。
`dispatcherInstanceId` 标识实例；`targetKey` 标识该实例中的具体执行目标，只要求实例内唯一，
不要求全平台唯一。不是给整个 Dispatcher 只配置一个环境键，也不以 URL 代替实例身份。

例如，同一 Dispatcher 实例可以提供下列目标，并分别绑定三条计算引擎记录：

| 目标键 | 后端类型 | 执行环境 |
| --- | --- | --- |
| `docker-102` | `LOCAL_DOCKER` | 102 主机的 Docker |
| `k8s-test` | `KUBERNETES` | 测试集群的指定 Namespace |
| `k8s-prod` | `KUBERNETES` | 正式集群的指定 Namespace |

`targetKey` 不是用户任务的另一个选项：用户仍只选择平台计算引擎。它用于把 Admin 注册
对象对应到部署配置，解决同一个 Dispatcher 中存在多个同类型目标时的定位问题。

不得把已经使用的 `targetKey` 改指另一个集群或主机。连接地址、Namespace、YARN 队列等
改变执行目标身份时，应配置新键、新建引擎；同一物理目标的凭据轮换不等于更换目标。

## 4. 配置归属与启动行为

### 4.1 Dispatcher 部署配置

继续由部署配置维护物理连接和敏感信息，不把 kubeconfig、Docker Socket、Runner 路径或
集群凭据放进任务定义，也不要求在 Admin 中重复保存这些配置。
此处对接的是 Docker/YARN/Kubernetes 计算后端，不是数据服务发布使用的 Service Engine。
集群地址、Namespace、ServiceAccount、认证凭据和运行镜像等由对应目标封装；Admin 仍管理
计算引擎的准入容量；任务资源默认值和单次上限由 Dispatcher 的各目标 resource-policy 维护，Admin 只读登记。Topic 由 Dispatcher 实例配置提供。

下面仅展示配置层级。完整实例模板见 [application-instance.example.yml](../../../deploy/compute-engine/application-instance.example.yml)，
多后端运行镜像及覆盖配置见 `deploy/compute-engine/Dockerfile.multi-target`、`compose.multi-target.example.yaml`。
最多 32 个目标；键为 1～63 位小写字母、数字或连字符，以字母开头。`enabled` 默认 true。
未启用任何 targets 时不自动启用后端。旧式 default 目标仅在 legacy-single-backend=true 时启用；已有账本切换前必须迁移。

```yaml
data-scalpel:
  dispatcher:
    # 实例级保护；以下数值仅为结构示例，不是生产容量建议。
    max-concurrent-submissions: 4
    maintenance-concurrency: 8
    targets:
      docker-102:
        enabled: true
        backend: LOCAL_DOCKER
        # 现有 Docker 镜像、Runner JAR、工作目录等配置归入此目标。
      k8s-a:
        enabled: true
        backend: KUBERNETES
        # API Server、Namespace、ServiceAccount、镜像 digest、认证引用等。
      yarn-a:
        enabled: false
        backend: YARN
        # Spark/Hadoop 客户端、配置目录、队列、Runner cluster JAR 等。
```

公共配置保留在实例层：数据库、Kafka、MinIO、控制面端口与认证。目标配置包含现有后端的
连接参数、资源配置、超时和路径；不同目标的 CLI 认证及环境变量必须按调用显式传入，
不能通过修改进程全局环境或默认 kubeconfig context 来切换集群。

未启用的目标不要求安装对应客户端，也不检查其远端连通性。配置本身非法应阻止启动并
明确报错；某个合法目标暂时网络不可达只使该目标不可用，不阻止其他目标运行。

第一版不增加热更新：修改目标配置需受控重启。已有排队、活动或待清理执行的目标不能
直接删除配置；先停止新准入并处理完执行及清理责任。意外遗漏配置时保留账本和诊断，
不得丢弃记录或改投其他目标。

### 4.2 Admin 计算引擎配置

Admin 可维护名称、Dispatcher 地址/Token、准入容量；后端类型、目标键、Topic 和资源策略从发现结果取得，只读展示。
连接测试返回 Dispatcher 的目标目录，页面选择目标后展示其后端类型、健康和安全摘要。

注册校验：实例身份一致、目标存在且启用、后端类型匹配、目标未被其他活动引擎占用。
后端类型由目标选择结果自动带出并只读展示，服务端仍严格校验，不再让用户独立选择一个
可能与目标矛盾的后端类型。

目标目录不返回凭据、完整物理路径、任意 Spark Conf 或 kubeconfig 内容。不新增 Dispatcher
管理业务模块；先在现有计算引擎创建、编辑和详情页面完成目标选择及状态展示。

### 4.3 目标发现与注册方向

沿用 Admin 发起注册、Dispatcher 校验并确认绑定的方向，不新增 Dispatcher 主动向 Admin
注册或服务发现流程。用户无需手工生成实例 ID，也无需把集群连接配置再录入 Admin。

1. 用户在 Admin 填写 Dispatcher 地址及访问 Token；Admin 经认证读取实例身份和目标目录。
2. 页面列出已启用目标，以目标键、后端类型和健康摘要帮助用户选择；不要求用户凭记忆手填键。
3. 用户勾选一个或多个目标并配置各自名称和准入容量，查看各目标的只读资源默认值与上限；每条引擎记录只选择一个目标。
   新登记使用发现结果的实例级 command/runner-event/admin-event Topic；Admin 校验事件通道受本平台监听，页面只读。
4. 注册时 Admin 携带 `engineId`、所选 `targetKey` 及注册策略调用 Dispatcher；双方核对实例
   身份、目标启用状态、后端类型和占用情况，Dispatcher 确认绑定，Admin 保存确认后的身份与状态。

目标目录读取不等于注册成功。目标在读取后被禁用、变更或被其他引擎占用时，注册应明确拒绝，
不得自动改选另一个同类型目标。后续注册查询、运行概览及生命周期操作均核对该实例和目标绑定。

### 4.4 任务资源配置

- 每个目标维护 resource-policy.defaults / maximums，注册时 Dispatcher 拒绝与部署策略不一致的请求。Admin 的新目标注册接口忽略旧客户端传入的 resourcePolicy，始终读取目标发现结果；编辑已绑定目标时拒绝修改策略。
- 默认值不是探测的集群容量，上限也不是总配额。部署人员按环境容量和使用约定配置，各维度默认值必须不超过上限。具体 YAML 见[部署配置](../../operations/dispatcher-configuration.md)。
- Canvas（批/流）和模型质检在任务基本配置保存自定义资源；JAR（上传/在线、批/流）在已有任务定义运行配置维护资源。null / 使用引擎默认值不固化具体数值；自定义规格持久化到任务配置。
- 已发布任务先停用才能修改资源，活动实时任务还须先停止。任务发布及提交校验上限；立即运行、计划调度、工作流触发均读取任务配置，不提供仅本次运行的临时资源覆盖。
- Admin 在实际提交时解析资源，运行记录和 Kafka 命令保存同一份申请快照；Dispatcher 再按部署上限校验，后端使用快照。旧运行不跟随任务或默认值变化。
- 改 Dispatcher 策略需维护窗口重启，并对已有引擎停用注册后重新注册，拉取新的默认值和上限；Admin 不从浏览器提交策略。收紧上限不会改写旧任务，超限任务需自行修改；查询 JAR 定义仍可读取原值用于修正。
- LOCAL_SQL 不使用 Spark 资源，WORKFLOW 本身不申请 Spark 资源，其子任务使用各自配置。

## 5. 路由与执行身份

正常链路：用户给任务选择计算引擎，Admin 将本次运行绑定的 `engineId` 写入执行命令；
Dispatcher 验证注册后解析为固定 `targetKey/backendType`，持久化后才进入提交阶段。

- Spark Canvas、Spark JAR 以及对应流式任务，使用其明确绑定且具备所需能力的引擎。
- 任务类型决定执行载荷和能力要求，不决定 Docker/YARN/K8s 提交位置。
- `LOCAL_SQL` 等原来不走 Dispatcher 的执行路径不因本次改造而迁入 Dispatcher。
- 引擎不支持任务所需能力时明确拒绝，不自动改投其他引擎。
- 查询、取消、超时处理、日志、终态清理、重启恢复均从执行记录定位原目标；不能读取任务
  当前绑定并据此改路由，也不能按 `backendType` 随意挑选同类型的另一个目标。
- 原目标配置暂时缺失或不可访问时保留现有恢复/超时语义，报告具体引擎不可用，禁止盲目重新提交。

执行记录增加稳定目标键，并核对已有 `engineId/backendType`、资源快照和外部执行身份。
配置修订需有可比较的非敏感版本或摘要，避免重启后悄悄用另一组物理目标参数操作旧执行。
不复制凭据到执行记录；有未完成执行或待清理资源时，应保留原目标连接能力。

## 6. 队列、准入与隔离

### 6.1 逻辑队列独立，不按引擎建表

复用 `DispatcherTaskExecution`，按 `engineId` 查询和计数。每个引擎独立维护：

- `maxQueuedExecutions`：等待队列上限。
- `maxConcurrentSubmissions`：正在调用外部提交的上限。
- `maxInFlightApplications`：在途应用上限，保留现有状态计算口径和 0 的含义。
- 按 `queuedAt, executionId` 排序的 FIFO 队列，以及引擎内队列位置。

只锁定需要准入的引擎注册及执行记录，不通过锁定所有注册来串行化整个实例。
准入计数和状态变更在短事务内完成；外部命令继续在事务外执行。

### 6.2 调度公平与实例保护

调度器在可准入引擎之间轮询，单轮有界领取，防止一个大队列持续占据全部提交机会。
领取前同时满足引擎额度和实例级提交槽位；线程池容量有界，不把持久化排队搬进无界内存队列。
提交超时、异常和提交结果不确定时均需正确释放本地槽位，并按原执行状态恢复，不能重复提交。

提交、状态观测、取消和清理不能在一个串行调用链上互相阻塞。使用有界并发、每引擎限额和
命令超时，避免某个不可达集群使其他引擎无法获得维护机会。不要求每个目标独占一套大型线程池。

实例总在途上限是否需要新增配置留待容量验证；第一版必须具备提交并发及后台 I/O 的整体保护。
长期运行的流式应用计入原有在途额度，不能为了批任务调度自动停止它们。

### 6.3 隔离承诺的边界

独立队列意味着某个引擎队列满、停用或后端故障不会直接阻塞其他引擎的准入和状态处理，
并不等于绝对资源隔离。实例仍共享 JVM、CPU、内存、数据库连接、Kafka 和 MinIO；
同一 Docker 主机或 K8s 集群的多个引擎也可能共享底层资源。

进程崩溃、公共数据库/Kafka 故障会影响全部绑定引擎。需要硬隔离或不同网络权限时，仍拆实例。

## 7. 注册、消息与控制面

### 7.1 注册与监听器

注册记录改为按 `engineId` 操作，移除“取第一条作为当前注册”的假设。注册状态、
容量、最近错误和目标绑定属于具体引擎；Topic 由实例维护，注册记录保留确认快照；`dispatcherInstanceId` 为实例级稳定身份。

v3 的命令和 Runner 监听器按实例管理，消费者组使用持久化实例 ID。引擎暂停/恢复/停用不能停止公共监听；运行时继续校验消息 engineId 的注册与执行归属。旧 v2 引擎级监听仅在显式兼容模式保留。

沿用现有消息体系，不增加新的消息中间件：

- 命令 Topic、Runner 事件 Topic、派生的实时控制 Topic 由实例共享；实时 Runner 仍使用执行级独立消费组及身份校验。
- Admin 事件 Topic 可以共享，依据 `engineId` 校验和路由。
- 消费入口核对 Topic 归属、消息 `engineId`、执行身份和账本归属，错投消息不能操作其他引擎。
- Inbox/Outbox、幂等指纹及顺序号保留；仅变更调用方查找注册和目标的方式。

并发注册需要数据库唯一约束或现有短事务串行化手段保证目标不被重复占用，不能只依赖
“先查后写”。继续使用 UUID 和标量引用，不增加跨 Schema 外键。

### 7.2 HTTP 与展示

建议将控制面分为实例信息和引擎信息：

| 类别 | 建议行为 |
| --- | --- |
| 实例信息 | 返回实例 ID、版本、多引擎能力标记、已配置目标的安全摘要 |
| 注册查询/命令 | 按 `engineId` 查询、激活、Drain、反注册；激活请求带目标键 |
| 引擎运行概览 | 按 `engineId` 返回注册、健康、队列、额度及后端安全配置 |
| 执行列表/详情/操作 | 明确引擎范围；详情或命令必须核对执行与引擎归属 |
| 实例汇总 | 如展示总数，明确标注实例范围，不与引擎队列位置混用 |

Admin 使用 `POST /api/v1/compute-engines/actions/query-targets` 只读发现，
`POST /api/v1/compute-engines/actions/register-targets` 逐项注册，返回逐项结果；部分失败不回滚成功项。
相同实例与目标键重复登记复用已有记录，不因 URL 别名创建第二条。实例 ID、目标键和物理指纹固定绑定。
批量部分失败或手动刷新发现结果时，同一实例、同一物理指纹且尚未创建引擎的目标，保留用户填写的名称和准入容量；资源策略始终更新为最新发现结果；已有引擎以服务端保存配置为准。连接、实例身份或目标物理指纹变化时，不把原草稿复制到新的执行环境。
继续使用 GET/POST、ProblemDetail 和中文 OpenAPI；Admin Dispatcher Client 和页面类型同步更新。不通过给
旧单值 `backendType` 随意挑一个返回值来伪装兼容。

## 8. 生命周期、健康与故障恢复

### 管理页面用语与消息通道（2026-09-27）

强制停用在同一事务中取消排队账本并写入 `EXECUTION_CANCELLED` Outbox，不能只改状态而漏发终态。活动任务仍由既有监管流程取消并确认外部资源清理；重复停用不重复生成排队取消事件。Admin 强制停用的远端读取超时至少 60 秒（普通调用仍沿用配置，默认 10 秒），前端该操作等待 90 秒，以覆盖 Dispatcher 默认 30 秒清理窗口；超时仍需查询实际状态，不意味着撤销或成功。若部署方延长 Dispatcher 清理窗口，也须协调 Admin `request-timeout` 和前端等待窗口。

发现注册不要求用户填写 Topic。v3 由 Dispatcher 返回部署配置中的实例通道，Admin 保存并核对，禁止逐引擎修改。Admin 事件 Topic 默认 datascalpel.execution.event，必须与平台监听配置一致。旧 v2 保留兼容，但切换 v3 必须经维护窗口迁移，不因刷新发现静默改名。
列表提供“查看全部 Topic”，详情、修改配置及发现结果的已注册项展示三个实际保存的只读通道，均可复制；旧式手工创建 API 保持兼容。

| 页面操作 | 实际语义 |
| --- | --- |
| 暂停任务调度 | 原 Drain；拒绝新任务并暂停领取既有队列，运行中任务继续；不自动停用 |
| 恢复任务调度 | DRAINING → ACTIVE，继续领取原队列；不更改 Topic/配置，不重启运行任务，不撤销已有取消请求 |
| 停用引擎（需无任务） | 原安全反注册；有排队、活动执行或待清理责任时拒绝，不自动取消；配置和历史保留 |
| 取消全部任务并停用 | 原强制反注册；仅取消此引擎全部排队/运行任务，等待终态和清理；已写入数据不回滚，超时不能解释为完成 |

恢复走 `POST /api/v1/compute-engines/{id}/actions/resume`，Admin 核对完整注册归属后调用 Dispatcher `/api/v1/dispatcher/registration/actions/resume?engineId=...`。Dispatcher 在引擎生命周期锁内检查目标绑定、就绪和监听，再以短事务改状态；不经过反注册重建队列，不改变 Kafka 执行协议。旧 Dispatcher 未提供此端点时须升级，不能退化成强制反注册来模拟恢复。

- Drain 只作用于指定引擎：拒绝新 SUBMIT、暂停领取该引擎已有 QUEUED；继续处理在途执行、
  CANCEL、Runner 事件和清理。这沿用当前暂停准入的语义，不把本次改造变成自动排空新策略。
- 已排队任务保留，恢复 ACTIVE 后继续；若要反注册，应先取消排队并处理活动执行。强制
  反注册沿用既有取消与状态收敛逻辑，不把停止监听等同于取消成功。
- 只有该引擎的活动执行和待清理外部资源责任已处理，才能移除其目标配置。其他引擎不受此操作影响。
- 外部集群暂时不可达时，将对应引擎标记不可用、停止新提交；继续允许查询和必要的有界恢复探测。
- 实例就绪反映公共服务是否可工作；单目标故障通过引擎健康报告，避免触发整个 Dispatcher
  反复重启。公共数据库/Kafka/制品存储故障仍影响实例就绪。所有目标不可用时控制面仍应可诊断，
  可提交引擎数量为 0，不报告“所有引擎健康”。
- 重启后按原引擎/目标/执行标签恢复外部身份，保持提交不确定、结果归档、Outbox 和终态
  清理机制；不因注册记录顺序改变而接管错误集群。
- 独立 Dispatcher 部署仍使用独立身份和执行账本 Schema；不能直接多开进程共享同一账本，
  并将其理解为本方案提供的高可用。

## 9. 部署与运行包

Dispatcher 可以运行在 K8s 集群外，包括现有 102 主机。须具备到目标 API Server 的网络、
客户端和认证权限；Driver/Executor 还须访问 Kafka、MinIO 和任务所需数据源。

- Local Docker 保留当前基础运行镜像加挂载 `runner-local.jar` 的方式。
- Kubernetes 使用包含 Spark/Java 及 `runner-cluster.jar` 的专用镜像，按不可变 digest 配置。
- YARN 使用现有 cluster Runner 分发方式。
- 同一进程管理多个目标不改变 Runner 的一次性执行及制品协议，不把不同后端的打包方式混用。
- 现有 `deploy/compute-engine` Docker 部署不能只增加一条 K8s 配置就宣称可用；还需准备
  Spark/kubectl 客户端、认证挂载及网络。部署脚本必须按启用目标检查所需依赖。

## 10. 代码改动范围

| 位置 | 必要调整 |
| --- | --- |
| `data-scalpel-task-dispatcher/config` | 多目标配置、启用校验、实例总并发保护；公共配置与目标配置分离 |
| `backend` 及三种实现 | 保留 `TaskExecutionBackend`；按目标构造现有实现，用简单目标映射查找，不引入动态插件 |
| `domain/repository` | 注册目标键、执行目标绑定、必要索引和约束；队列查询按引擎限定 |
| `management` | 多注册、目标发现、引擎级生命周期及健康；监听器按引擎管理 |
| `service` | 引擎级准入、公平调度；提交、观测、取消、清理和恢复使用固定目标 |
| Dispatcher 消息消费/Outbox | 审计单注册假设、Topic 归属与跨引擎误路由，保持原幂等和终态链路 |
| `data-scalpel-business/compute` | 保存目标键、连接测试、注册、运行概览和解绑逻辑适配多目标 |
| Business 任务执行链路 | 保留任务显式绑定；核对运行快照、命令及状态对账的引擎身份 |
| `data-scalpel-ui/modules/computeengine` | 目标选择、同地址多引擎、独立容量和健康显示；任务选择方式不变 |
| Contracts / Dispatcher Client | 仅修改实际受影响的稳定契约；不把部署凭据下发到任务 |
| `deploy/compute-engine` 与部署脚本 | 配置模板、依赖检查、旧配置迁移和回退说明 |

本次不修改 Canvas 算子、模型类型系统、在线 Java 编辑器或用户 SDK API；不增加按负载
自动选集群、跨引擎重试/迁移、集群资源实时调度、ABAC 或多租户权限体系。

## 11. 升级与回退建议

1. 保存旧部署配置、数据库备份和程序包；确认现有引擎、Topic、执行和清理状态。
2. 第一版采用维护窗口升级：Drain 旧实例，取消或处理排队任务，等待/主动停止活动任务，
   完成制品归档和外部清理后再切换；流式任务需要明确安排停止和后续恢复。
3. 将旧单值后端配置显式转为一个稳定目标键；旧引擎和执行记录补齐该目标，保留实例 ID、
   引擎 ID 和历史执行身份，不通过重新创建引擎丢弃历史关联。
4. 数据回填由明确的受控升级步骤完成，不能假设 `ddl-auto=update` 会完成业务数据迁移。
   不为本次改造引入新的数据库迁移框架。
5. Admin/Dispatcher 使用明确的能力或控制面版本协商。新 Admin 连接旧单目标 Dispatcher
   时只能使用旧单目标路径；旧 Admin 不得误操作多目标实例。版本不匹配应明确提示升级。
6. 先验证原 Local Docker 目标，再增加第二个目标，最后接入真实 K8s/YARN 集群验证。
7. 引入多注册后不能直接换回旧程序让其“读取第一条注册”；回退须停止新准入、处理活动
   执行和清理责任，并恢复匹配的单目标配置及账本。不得覆盖仍有运行结果的数据库来强行回退。

## 12. 建议验证场景与评审项

以下为验证范围，不代表每项均已实测；实际执行事实以[验证记录](../../verification/kubernetes-submission-20260926.md)为准。

| 场景 | 需要证明的结果 |
| --- | --- |
| 单目标升级 | 原 Local Docker 路由、身份、结果与历史记录保持正确 |
| Docker + K8s 同实例 | 同种任务可显式选不同引擎，实际进入正确执行目标 |
| 两个 K8s 目标 | 相同后端类型不同 Namespace/集群，不串提交、日志、取消和清理 |
| 队列与并发 | 引擎内 FIFO、独立额度、实例总提交上限、公平轮询不饥饿 |
| 一个目标慢/不可达 | 其他目标仍能提交、取消和更新状态，控制面可诊断 |
| Drain/反注册/禁用 | 仅影响目标引擎；排队、活动及待清理资源不被遗弃 |
| 重启与重复消息 | 按原目标恢复；无重复提交、误绑定及跨引擎状态覆盖 |
| 注册竞争/错投消息 | 目标不能被重复占用；错误 Topic/engineId 组合被安全拒绝 |
| 批流并存 | 长运行流任务计数准确，批任务不会改变其目标或强制抢占 |
| 真实 K8s 端到端 | 任务提交、Driver/Executor、Kafka/MinIO 回传、取消和重启恢复闭环 |

已确认一目标一引擎绑定、Dispatcher 维护后端连接细节、Admin 选择目标并主动注册，详见第 3、4 节。
第一版采用启动配置、受控重启、原有 Drain 暂停队列语义和维护窗口升级，未实现动态增删部署目标或多版本无停机迁移。

## 13. 网络与操作边界

- Kafka 执行协议保留：Admin Outbox → 实例 command Topic → Dispatcher；Runner → 实例 runner-event Topic → Dispatcher；Dispatcher Outbox → 平台共享 admin-event Topic → Admin。分区 Key 继续使用 executionId；HTTP 发现/管理不替代 Kafka 提交和取消。
- Runner 的 MinIO 签名地址由 Dispatcher 的 `DATASCALPEL_FILE_STORAGE_RUNNER_ENDPOINT` 生成，必须对所有目标 Runner 可达。不能把只在 Docker 内有效的 `host.docker.internal` 直接用于 Kubernetes；本次使用 102 内网地址。
- 发现为只读；首次或过期就绪检查异步执行，目录 `checking=true`、`ready=false` 表示检查中而非检查失败，未完成时不允许注册。前端每 2 秒自动刷新检查结果，最多等待 60 秒；全部检查结束、请求失败或超时后停止并允许手动重试。关闭面板、修改地址或 Token 时取消请求，不使用旧连接的迟到结果。自动刷新保留同实例、同物理目标的名称与准入草稿；真正未就绪仍显示安全原因。滚动升级时兼容旧接口的固定“目标就绪状态检查中，请稍后刷新”提示。配置指纹不包含凭据/镜像版本，但包含目标物理身份，不能借凭据轮换改投其他环境。
- 注册失败可能已经建立远端关系，因此保留 ERROR 记录。应恢复连接后重试登记或安全反注册，不能直接删除不确定状态；离线解绑仍要求原实例永久停机的人工确认。
- 单目标暂停不等于进程级资源隔离。公共 Kafka、数据库、MinIO 或 Dispatcher 进程故障仍会影响全部目标。


## 两文件部署配置

内置 application.yml 与 application-instance.yml 的分工、97 项原配置逐项对应、后端默认覆盖和维护窗口迁移见[配置与迁移说明](../../operations/dispatcher-configuration.md)。
