# Dispatcher 两文件配置与实例共享 Topic 验证

日期：2026-09-27。范围：102 上一个 Dispatcher、Local Docker 102 和 Kubernetes 103 两个执行目标。不是生产容量或长期稳定性认证。

## 修改事实

- 内置 application.yml 保存默认值，包含 instance Profile；application-instance.yml 保存变动项。部署从工作目录 config/application-instance.yml 加载，不再使用额外 targets 文件导入。
- 原 application.yml 的 97 个叶子配置逐项核对；技术参数的环境变量入口保留。各后端默认项与目标显式项分开，目标覆盖优先。内置目标示例全部禁用，防止服务器意外启用调试目标。
- 发现协议 v3 返回实际共享消息通道；目标 Key 可见、可复制。Admin 新登记使用发现值，不生成逐引擎 Topic。
- 同实例共用命令/Runner 监听器，仍以 executionId 为 Kafka 分区 Key，由消息 engineId 绑定到目标。暂停/停用一个引擎不关闭实例消费者。
- 更新部署模板、Windows 部署脚本和本地统一启动脚本。脚本语法检查通过；未对 Windows 远程部署脚本执行完整重新部署。

## 自动化验证

| 项目 | 结果 |
| --- | --- |
| Dispatcher 全模块测试 | 82 项，81 通过、1 跳过；跳过项为受环境开关控制的外部 Kafka Topic 集成测试 |
| 本轮配置/路由/共享监听/双目标针对性测试 | 13 项通过，包含 Spring 加载 instance Profile、外置覆盖、默认值保留、目标覆盖隔离 |
| Admin 目标发现服务集成测试 | 6 项通过：同地址双引擎共享通道、重试复用、暂停恢复、身份及生命周期保护；使用 H2 与 Mock Dispatcher，不代替真实链路 |
| UI 交互回归 | 22 项通过，覆盖目标发现/通道展示、配置表单、生命周期操作；最初 20 秒单项限时有超时，单 Worker、60 秒限时复跑通过，不代表界面性能已达标 |
| TypeScript 类型检查 | 通过 |
| Admin / Dispatcher 及依赖打包 | 通过；打包时跳过测试，测试结果单列，不混淆 |

Admin 专项测试临时限制 testCompile 范围，避免被本轮无关测试编译问题阻断；限制已移除，未据此声称 Admin 全量测试通过。

## 维护窗口迁移

用户在 IDEA 停止 Admin 后执行。两端无待投递 Outbox；Dispatcher 无活动执行和待清理外部资源。原有 11 条 Dispatcher 执行记录保留。

- 102 发布目录：`/data/datascalpel-compute-engine/releases/20260927-shared-config`。
- 旧 JAR 不覆盖；注册记录、原 Compose/环境配置/目标配置备份到该发布目录 backup，权限限制为运行维护账号可读。备份含敏感配置，不入 Git。
- 新外置文件：`/data/datascalpel-compute-engine/compose/application-instance.yml`，挂载到 `/app/config/application-instance.yml`，容器工作目录 `/app`。
- 当前 Compose 启动文件：`/data/datascalpel-compute-engine/compose/compose.shared.yaml`，继续使用原项目名 datascalpel-compute-engine。
- 新 Dispatcher JAR SHA-256：`80df13681dd9e7a62471561446f0da2dc911560f26cda9398229a5f6e356dcde`。
- 只移除 compute_engine 的两个旧 Topic 唯一约束；名称和目标绑定唯一约束保留。两张注册表的通道修改在同一数据库事务内完成。
- 实例 ID、两个引擎 ID、目标 Key、目标指纹、任务关联与历史消息记录不变。未修改历史 Outbox 的投递地址。
- 新命令 Topic：`datascalpel.execution.command.dispatcher102`。
- 新 Runner Topic：`datascalpel.runner.event.dispatcher102`。
- Admin Topic 仍为 `datascalpel.execution.event`；Runner control 由新 Runner Topic 加 `.control` 推导。
- 旧 Topic 未删除。切换后补查旧四个消费组积压均为 0：命令 Docker 24/24、K8s 24/24；Runner Docker 57/57、K8s 31/31。

远端新进程已返回 v3、正确通道和原实例/目标身份；两个目标在一次检查中均 ready=true。目标检查缓存过期时返回“检查中”，不能把该瞬时状态等同于网络故障。

## 真实链路回归进展

当前等待用户在 IDEA 启动新版 Admin，再进行 Docker/Kubernetes 实际任务、隔离操作、页面和 OpenAPI 验证。尚未完成的验证不能由上面的单元/服务测试替代。

### 用户重新全流程测试前的数据清理

2026-09-27，用户明确要求不修改任务删除机制，清理已有任务及引擎数据，重新测试创建引擎到发布任务。用户在 IDEA 停止 Admin 后，核对无活动任务、无待清理执行、双方 Outbox 已发布、Kafka 消费无积压，临时停止 Dispatcher 并执行一次性维护清理。

- 清理 6 个任务、13 条 Admin 运行记录、1 条 Admin 引擎记录、2 条 Dispatcher 注册及 11 条 Dispatcher 执行记录；同时清理关联定义、资源引用、血缘（含既往自测遗留）、消息账本及关联告警历史。
- 备份后删除 39 个任务制品对象（837,939 字节），11 个对应执行/检查点目录移入备份，不删除模型及目标业务表数据。
- 数据源、模型、目录、全局告警规则及其他未选中数据库行，按清理前后内容摘要核对一致。保留 Dispatcher 实例身份、Kafka Topic 和消费位点。
- 可恢复备份位于 102 的 `/data/datascalpel-compute-engine/backups/task-engine-reset-20260927`，包含数据库行快照、制品归档及移出的目录；仅属主可访问，不提交仓库。
- Dispatcher 已恢复启动；任务、运行、引擎及远端注册记录均为 0，两个配置目标返回 UNREGISTERED。本操作没有修改业务删除规则，也没有创建或发布新任务。

## 边界与回退

- 本轮没有真实 YARN 环境，不声称 YARN 已联调通过；配置项和原后端代码保留。
- 共享 Kafka 分区与 JVM 不提供目标间硬吞吐隔离。队列/容量/生命周期按引擎隔离。
- 旧 Topic、上一版 lifecycle 发布包及本次升级备份保留；更早文件按下节清理。切换后若已有新任务，禁止直接恢复旧注册库快照覆盖新历史；回退必须重新确认空闲和消息收敛。

## 配置精简（2026-09-27）

- 源码实例配置与部署示例去掉重复的 Admin 事件 Topic、制品存储和 Runner Kafka 通用默认项；环境变量别名保留在默认文件中。部署示例同时去掉重复的 Docker 技术默认值。
- 修正示例中三个目标误共用旧全局 backend 环境变量的问题，分别明确为 LOCAL_DOCKER、KUBERNETES、YARN。102 实际两个目标原本类型正确，不受该示例错误影响。
- 102 外置 `/data/datascalpel-compute-engine/compose/application-instance.yml` 去掉三个禁用示例，以及与运行中 JAR 默认配置、当前环境核对一致的 11 个重复项。两个真实 targetKey、后端类型、连接与认证、资源的非默认覆盖、Runner 时区、目录和消息通道保持原语义。
- 原文件保存在 `releases/20260927-shared-config/backup/application-instance.before-simplify.yml`，配置和备份均限制为仅属主读写。远端 Compose 校验通过，容器 ID、启动时间不变，健康状态仍 healthy；本轮没有重启或替换远端 JAR。
- `DispatcherConfigurationTest` 5 项全部通过，覆盖默认加载、外置覆盖、目标隔离、后端类型及保留的环境变量覆盖。此次验证不替代上文尚未完成的真实任务回归。

## 部署目录清理（2026-09-27）

### Compose 应用环境变量收敛（配置精简后的追加操作）

102 的 compose.shared.yaml 移除 30 个 DATASCALPEL_* 应用环境变量，environment 仅保留 JAVA_TOOL_OPTIONS、TZ。使用运行中 JAR 的两份配置、外置实例文件及旧容器环境，对两个启用目标涉及的 95 项有效配置进行前后比较，值一致；禁用示例不纳入启用目标比较。

确认本机 Admin 未监听 8080、双方 Outbox 无未发布记录、Dispatcher 无活动执行及待清理资源后，使用原 Compose 项目和文件重建 Dispatcher 容器。重建后确认应用环境变量已清除；镜像/JAR 启动命令及其他 Compose 项不变，实际挂载与声明一致。健康状态 healthy，发现接口的 local-docker-102、k8s-103 均 ready=true。首次发现返回检查中，后续检查完成后就绪；没有触发真实业务任务。

修改前 Compose 保存在 releases/20260927-shared-config/backup/compose.before-env-cleanup.yaml，属主读写权限。application-instance.yml 未修改，数据库记录与消息通道未迁移。本次没有运行或改造仓库原 linux69 环境变量式部署脚本。

按用户要求，在核对所有容器挂载、运行参数、当前配置及回退 Compose 的实际解析结果后，清理未引用的旧发布包、旧 Compose/.env、旧 targets 文件、旧构建目录和更早的临时备份，释放 4,879,248,220 字节（约 4.54 GiB）。部署目录由约 5.8 GiB 降到 1.3 GiB。

清理后保留当前 shared-config Dispatcher、正在使用的 runner-r3、上一版 lifecycle Dispatcher，以及 shared-config/backup 下的一份升级备份。当前配置只维护 compose/application-instance.yml 和 compose/compose.shared.yaml，Kubernetes 凭据仍在 config/dispatcher.kubeconfig；state 执行目录未动。

更早发布包已永久删除，需要时重新构建，不能再按其旧路径直接回退。上一版回退所需的 Compose/.env/targets 副本在升级备份内；恢复时须显式恢复对应配置文件，不能直接启动已删除的顶层旧 Compose 文件。本次未重启容器，容器 ID、启动时间、健康状态和当前配置摘要均验证未变；未删除数据库记录、镜像或 Kafka Topic。
