# Dispatcher 资源配置逐链路核对

日期：2026-09-27。范围：本轮资源策略、任务配置、三种提交后端；不表示 YARN 已完成真实集群验收。

## 实际执行语义

| 项目 | Local Docker | Kubernetes | YARN |
| --- | --- | --- | --- |
| 执行方式 | 一个容器内 local[*] | cluster 模式 Driver/Executor Pod | cluster 模式应用 |
| Driver CPU | Docker --cpus 限额 | Driver CPU request | --driver-cores |
| Driver 内存 | 容器总内存；堆取 75% | Spark JVM 堆内存 | Spark JVM 堆内存 |
| Executor 三项 | 不适用；配置文件不再列出 | 实例数、核数、每个 JVM 堆内存 | 实例数、核数、每个 JVM 堆内存 |
| 资源权威来源 | 每个 target 的 resource-policy；任务可保存自定义 | 同左 | 同左 |
| 运行概览 | 显示目标默认值，不是任务申请或总上限 | 同左 | 同左 |

Kubernetes CPU request 不等同于 CPU 硬限制；本轮没有擅自新增 CPU limit 策略。Kubernetes/YARN 容器还需非堆开销；策略中的堆内存上限不是容器总内存上限，也不是集群总容量或资源预留。Spark 配置语义按仓库版本核对：[Spark 4.1.1 配置](https://spark.apache.org/docs/4.1.1/configuration.html)、[Kubernetes 参数](https://spark.apache.org/docs/4.1.1/running-on-kubernetes.html)。

## 修正内容

- Local Docker 配置仅需 Driver CPU/内存。内部旧消息继续保留 Executor 占位，但忽略其上限并在提交快照规范化；集群配置缺失 Executor 项仍拒绝。
- 发现/注册/运行概览统一读取目标策略；概览不再从旧后端字段返回与实际提交不一致的值。
- 删除默认 YAML 中不参与实际提交的重复后端资源项。旧 Java 字段仅兼容绑定，不作为当前生效入口。
- K8s 显式提交 Driver/Executor CPU request，避免 Spark 安装目录旧默认值覆盖任务 CPU 申请；K8s/YARN 显式关闭动态分配，保持已保存的固定 Executor 数量。用户配置排列在平台固定配置之前。
- JAR 页面不再把 GiB 向下取整；保留 MiB 精度。资源说明区分容器内存与 JVM 堆内存。
- 拦截通过 -XX:MaxHeapSize 等 JVM 参数绕过任务资源表单的路径。
- 保持仅从任务配置读取资源，无本次运行覆盖；历史运行快照不改写。

## 其余配置判断

- 镜像、Runner JAR、集群地址、Namespace、ServiceAccount、YARN 队列/Hadoop 配置、工作目录、Checkpoint：均有执行用途，保留目标级维护。
- 数据库、认证、Kafka 通道和 Runner 可达地址、对象存储：有实际用途，保留实例级配置；不改动凭据或网络地址。
- 轮询、提交/命令超时、失败宽限、日志限制：属于技术控制，不是任务资源；保留内部默认、允许外置覆盖。
- 排队数、并发提交数、在途数：是准入容量，不是 CPU/内存，不与任务资源混用；沿用现有管理方式。
- 所有策略数值均为管理员设定，不是探测主机得出的容量。资源申请合理性和集群是否能调度成功是两回事。

## 验证边界

针对性测试覆盖配置绑定、三后端命令、资源上限、占位兼容、运行概览以及 JVM 参数校验。前端覆盖配置表单并进行 TypeScript 检查。真实远端更新与任务执行以部署后的验证记录为准；本文件不能替代 Docker/K8s/YARN 全链路验收。

## 102 部署记录

2026-09-27 已部署本轮 Dispatcher 资源策略修正：

- JAR：`/data/datascalpel-compute-engine/releases/20260927-resource-policy/dispatcher.jar`。
- SHA-256：`4659af7c04359ed608ebc80cff110ca6fd0998449eaf30c2940492fb156c2a86`。
- 外置配置仍为 `/data/datascalpel-compute-engine/compose/application-instance.yml`，Compose 入口仍为同目录 `compose.shared.yaml`。
- 原 JAR、两份配置及发现结果备份在该版本目录的 `backup/` 下。
- 外置策略数值沿用原平台默认策略，并非按物理容量自动测算；删除不参与提交的旧后端资源项与 Docker 固定 `-Xmx`。Docker 配置不含 Executor 三项。
- 重建前后无活动/待清理执行、无未投递命令/事件。未修改数据库业务数据，没有注册新引擎或创建测试任务。
- 容器健康检查通过，`local-docker-102` 与 `k8s-103` 均 ready，发现接口返回策略。实例 ID、目标 Key、指纹与消息通道保持不变。
- 未重启本机 IDEA Admin，未更换 Runner JAR 或 K8s Runner 镜像。本次为部署和目标就绪核验，不代表真实任务端到端回归已完成。

### 就绪检查自动刷新修正

同日追加修正：目录提供 `checking` 标识；前端检查中每 2 秒自动查询，最多 60 秒，真实失败/超时停止并支持重试。地址或 Token 改变、面板卸载取消请求；同目标的表单草稿与有效选择保留。兼容未透传新字段的旧 Admin 固定检查中提示。

- 前端发现面板 12 项测试通过，TypeScript 检查通过；后端缓存及多目标集成共 9 项测试通过，Dispatcher 打包成功。
- 102 更新为 `/data/datascalpel-compute-engine/releases/20260927-readiness-refresh/dispatcher.jar`，SHA-256：`297dff1dd73a40f8d9669e59ece8d1b2503959d41ba234e31239e16aed1142d9`。上一版本及配置在该目录 `backup/` 中。
- 保留资源策略、实例身份、目标指纹及 Topic，健康检查通过，两目标 ready。未重启 IDEA Admin。
- 使用现有 `localhost:8887/compute-engine` 页面，只点击一次“连接并发现”：两行首先显示“检查中”，随后自动变为“目标就绪”，勾选框恢复可用。没有点击注册、创建引擎或运行任务。
