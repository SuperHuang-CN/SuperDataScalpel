# 192.168.5.103 单节点 Kubernetes 联调环境

部署日期：2026-09-26。用途：DataScalpel Kubernetes 提交后端的临时程序对接，不是生产集群、性能基线或高可用环境。

## 环境与入口

| 项目 | 实际配置 |
| --- | --- |
| 服务器 / 节点名 | `192.168.5.103` / `k8s103` |
| CPU / 内存 | 8 vCPU / 约 15.5 GiB |
| 磁盘 | 50 GiB；根分区扩展至约 47 GiB，另有启动分区与原 swap LV |
| 操作系统 | CentOS 7.9，内核 `5.4.278-1.el7.elrepo.x86_64`（旧 3.10 内核保留） |
| Kubernetes | kubeadm 安装，已逐版本升级至 `v1.32.13`，控制平面兼作工作节点 |
| 容器运行时 | containerd `1.6.33`，systemd cgroup |
| 网络 | Flannel `v0.26.7`，CNI 插件 `v1.6.2-flannel1` |
| Pod / Service 网段 | `10.244.0.0/16` / `10.96.0.0/12` |
| Helm / KubeSphere | Helm `v3.17.3`；KubeSphere `v4.1.3`，Chart `1.1.4` |
| Kubernetes API | `https://192.168.5.103:6443` |
| KubeSphere 控制台 | `http://192.168.5.103:30880`，用户名 `admin` |
| 任务命名空间 | `datascalpel-test` |

KubeSphere 是部署在集群内的 Web 管理平台，使用浏览器访问，不需要另装桌面客户端。仅部署核心组件，未安装 DevOps、日志存储、服务网格等扩展，也未安装动态存储供应器或完整监控套件。

**临时兼容组合（用户已确认）：** Spark 4.1.1 要求 Kubernetes ≥1.32，因此在备份后将内核升级到 5.4，Kubernetes 按 `1.30.14 → 1.31.14 → 1.32.13` 逐级升级，节点及核心组件恢复就绪，真实 SparkPi 已运行成功。保留的 KubeSphere 4.1.3 官方验证范围止于 Kubernetes 1.30，当前属于超出该矩阵的临时兼容测试，不能宣称官方支持。CentOS 7 和 EL7 的 5.4 内核归档包均已停止维护，不能直接作为公司生产环境基线。[Spark 4.1.1 部署前提](https://spark.apache.org/docs/4.1.1/running-on-kubernetes.html#prerequisites)、[CentOS 生命周期](https://www.centos.org/centos-linux/)、[KubeSphere 4.1 支持范围](https://www.kubesphere.io/docs/v4.1/03-installation-and-upgrade/01-preparations/01-supported-k8s/)。

升级前备份在 `/root/datascalpel-k8s103-bootstrap/upgrade/before-132`，包含 `configuration.tar.gz` 和 `etcd.db`；本机受限凭据目录中保留对应 `before-132-` 副本。新内核已实际重启验证并设置为默认，旧内核仍可在 GRUB 中选择；内核回退不等于 Kubernetes 降级，禁止直接恢复旧 etcd 覆盖后续业务数据。containerd sandbox 镜像同步改为 pause 3.10，etcd 为 3.5.24-0。

## 凭据与权限

凭据不进入 Git、本文或业务配置示例。

- 本机交付目录：`C:\Users\Lihuan\Desktop\data\k8s103-access`，限制为当前 Windows 用户和 SYSTEM 访问。
- `admin-password.txt`：初始随机 KubeSphere 管理员密码，不使用服务器登录密码。用户已修改服务端密码，本机旧副本失效。2026-09-27 使用用户提供的当前密码完成 `admin` 登录及认证后用户、节点、任务 Pod 查询，均成功；未重置密码，也未把新密码写入该文件或仓库。浏览器页面操作与认证接口验证的边界见联调记录。
- `dispatcher.kubeconfig`：供集群外 Dispatcher 使用，含 API CA 和专用令牌。
- 服务器管理员 kubeconfig：`/root/.kube/config`；不要将此集群管理员凭据提供给 Dispatcher。
- `datascalpel-dispatcher` ServiceAccount：仅允许在 `datascalpel-test` 管理任务所需的 Pod、Service、ConfigMap、Secret 及读取 Pod 日志；额外只允许读取该命名空间自身的信息，以满足 readiness 检查。
- `spark-runner` ServiceAccount：允许在同一命名空间管理 Pod、Service、ConfigMap，供 Driver 管理 Executor。

这些 RBAC 限制用于可信内部任务，不是恶意代码沙箱，也不代表该命名空间内的任务彼此隔离。

当前 Dispatcher 令牌到期时间：**2026-10-26 14:48:50（北京时间）**。需要续期时，在 103 上执行：

```bash
bash /root/datascalpel-k8s103-bootstrap/issue-dispatcher-kubeconfig.sh
```

然后重新分发 `/root/datascalpel-k8s103-bootstrap/dispatcher.kubeconfig` 到实际 Dispatcher，并按其部署方式重新加载。续期不会自动更新本机旧副本，也不会立即撤销仍有效的旧令牌。

控制台当前使用 HTTP，仅限可信内网临时测试，不要暴露公网。正式环境应配置 HTTPS、受控凭据与公司网络策略。

## 本次主机调整

1. 确认系统盘已有 50 GiB，但 `/dev/sda2` 仍约 19 GiB。保持分区起始扇区不变，扩展 sda2，依次执行 `pvresize`、`lvextend`、`xfs_growfs`，将剩余容量加入根挂载点。未重建文件系统、未删除原数据。
2. 关闭 swap 并禁用其开机挂载，保留原 swap LV；SELinux 调整为 permissive。此安全放宽仅用于本测试环境。
3. 开启容器网络所需内核模块、网桥转发及 IP 转发；containerd 使用 systemd cgroup。
4. 保留 firewalld。对 `192.168.5.0/24` 放行 TCP `6443`、`30880`；Pod/Service 网段及 CNI 接口加入 trusted zone。SSH 维持原规则。
5. 原网关 DNS `192.168.5.2` 多次查询超时，改为实测可用的 `223.5.5.5`、`119.29.29.29`，通过 NetworkManager 持久化。迁入公司内网后应改回公司指定 DNS。
6. 启用 containerd、kubelet 开机启动；移除单节点控制平面的不可调度 taint。
7. kubelet 为系统和 Kubernetes 各预留 `500m CPU / 768Mi` 内存；容器日志 `10Mi × 3`；镜像 GC 高/低水位 `75% / 60%`，磁盘可用空间低于 15% 触发驱逐条件。

原分区表、LVM 元数据、fstab、SELinux、SSH、主机名及 DNS 配置备份位于 `/root/datascalpel-k8s103-bootstrap`。分区和 LVM 扩展后的备份不等于可在线缩容方案；不要直接恢复旧分区表来“回退”。

## 制品与安装问题

- KubeSphere Chart 来自官方 `kubesphere/kubesphere` 仓库 `v4.1.3`，提交 `3ef3a6bc98b790480577e87fbc75bee5b3438862`；使用其自带 Chart 和子 Chart，不执行任意远程安装脚本。
- 安装中修正了 Chart 内 shell 脚本的 CRLF 换行问题，避免 CRD 升级钩子执行失败。
- Docker Hub/部分官方镜像源在此网络不可用。Flannel、KubeSphere 后端通过 DaoCloud 镜像代理拉取。控制台镜像改用 `swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/kubesphere/ks-console`，固定摘要 `sha256:08586c13d50f8f77b6175e37c123e0f3c4c3676b8dea39bae79fbc4cdbb5eec2`。
- 镜像源属于部署供应链的一部分；公司环境应使用自己的受控仓库，不依赖公网代理。控制台镜像摘要用于固定此次获取的制品，不等同于上游签名认证。
- Helm 下载包 SHA256 与官方校验文件一致：`ee88b3c851ae6466a3de507f7be73fe94d54cbf2987cbaa3d1a3832ea331f2cd`。
- 节点 RPM 安装使用单独、默认禁用的 Aliyun 镜像仓库文件 `datascalpel-bootstrap.repo`，保留 GPG 校验，未替换原仓库。CentOS 7 原在线仓库已失效，不要直接依赖普通 `yum update`。
- 当前生效的 Helm 配置：服务器 `/root/datascalpel-k8s103-bootstrap/kubesphere-values.yaml`；Chart 包、RBAC 清单、安装日志均留在同目录。目录中也含凭据，不要整体上传仓库。

## 已验证内容

| 检查项 | 结果 |
| --- | --- |
| 单节点及控制平面、CoreDNS、Flannel | Ready / Running |
| KubeSphere 核心 3 个组件 | 全部 Running / Ready；Helm 状态 deployed |
| 本机访问登录页、管理员登录 | 通过 |
| 登录后查询当前用户、节点、命名空间 | HTTP 200，返回实际 JSON |
| 限权 kubeconfig 创建 Pod、读取日志、删除 Pod | 通过，操作范围为 `datascalpel-test` |
| 限权 kubeconfig 向 `kube-system` 创建 Pod | 拒绝，符合预期 |
| 限权 kubeconfig 一次创建带完整身份标签的 Secret、读取并删除 | 通过；账号无 Secret patch 权限，验证不依赖额外 patch 授权；临时 Secret 已删除 |
| 测试 Pod 解析集群 DNS、访问 Kubernetes Service 443 | 通过 |
| 测试 Pod 访问 102 PostgreSQL 15432 / Kafka 9092 | TCP 连通 |
| 测试 Pod 访问 102 MinIO 9000 | `/minio/health/live` 通过 |
| 整机重启后恢复 | 节点及核心组件恢复就绪；登录与认证 API 再次通过，根分区、DNS、swap 设置保持 |

网络测试 Pod `integration-smoke-20260926` 已删除，日志保留为服务器 `integration-smoke.log`。TCP 连通不代表数据库认证、Kafka advertised listeners、读写权限或真实任务链路已验收。浏览器页面自动化未完成；登录和登录后接口使用 HTTP 会话实际验证。

初始空载观测：根分区约 4.5 GiB 已用、43 GiB 可用；系统内存约 1.4 GiB 已用、约 13.8 GiB available。升级、备份、两轮 Runner 镜像导入及测试资源/传输归档清理后，19:20 左右根分区约 12 GiB 已用、36 GiB 可用。数值是单次观测，不是压测结果。

## 启停、检查及磁盘维护

集群已设置开机启动。整机暂停使用时，先在平台停止提交并等待任务结束，再关机；重新启动虚拟机即可恢复。只停止 kubelet 不会可靠停止所有已有容器，因此不以 `systemctl stop kubelet` 作为整个环境的停机命令。

```bash
# 查看基础状态
systemctl status containerd kubelet --no-pager
kubectl get nodes
kubectl get pods -A
kubectl get --raw=/readyz
helm list -n kubesphere-system
df -h /
free -m

# 任务及日志（将占位名称换成实际 Pod 名）
kubectl -n datascalpel-test get pods -o wide
kubectl -n datascalpel-test logs <pod-name>

# 仅在任务已结束且日志已归档后，按确切名称清理遗留任务 Pod
kubectl -n datascalpel-test delete pod <finished-pod-name>
```

不要手工删除 `/var/lib/containerd`、`/var/lib/kubelet`、`/var/lib/etcd`；不要无差别删除命名空间或执行镜像全量清理。优先利用平台的任务清理逻辑，再按任务标签/确切名称核对残留 Pod、Secret、Service。镜像只保留必要的当前版本，预留至少 10 GiB 空间。

## DataScalpel 当前部署（2026-09-27）

102 原 `datascalpel-compute-engine` 容器已受控升级为单进程多目标 Dispatcher，同时管理 `local-docker-102` 和 `k8s-103`；103 不部署第二个 Dispatcher。Admin 已通过地址发现并登记 K8s，保留原 Docker 引擎 ID 和历史。真实成功、失败、运行/排队取消、重启接管、Drain 隔离及删除保护见 [联调记录](../verification/kubernetes-submission-20260926.md)。

102 部署文件：

```text
/data/datascalpel-compute-engine/compose/compose.yaml
/data/datascalpel-compute-engine/compose/compose.multi-target.yaml
/data/datascalpel-compute-engine/compose/.env
/data/datascalpel-compute-engine/config/dispatcher-targets.yml
/data/datascalpel-compute-engine/config/dispatcher.kubeconfig
/data/datascalpel-compute-engine/releases/20260927-lifecycle/dispatcher.jar
/data/datascalpel-compute-engine/releases/20260927-runner-r3/runner-local.jar
```

K8s 使用 `k8s://https://192.168.5.103:6443`、Namespace `datascalpel-test`、ServiceAccount `spark-runner`。受限 kubeconfig 以只读方式挂载到 Dispatcher 的 `/config/dispatcher.kubeconfig`，不交给 Admin 或浏览器。103 已预加载 Spark 4.1.1 / Java 21 / cluster Runner r3 镜像：

```text
docker.io/library/datascalpel-spark-runner@sha256:18f66c78052dbd6ec1fd4de0f7af59924cb9cbb4b53d30246196ce2c28c8176a
```

这不是公共镜像仓库地址，只有当前节点预加载，新增节点必须分发相同镜像或改用公司受控仓库。Local Docker 继续使用 Java 基础镜像挂载 local Runner，不混用 cluster JAR。r3 制品 SHA256：

| 制品 | SHA256 |
| --- | --- |
| runner-local.jar | `0341df5c8e581d4d649a8ecae98376a66a88420f218adc1895a3ed07cf661659` |
| runner-cluster.jar | `f9db17eb8125599eaa20b6a5c8864668f95a9c5011f3b1d6299cb6baa058aa97` |

Runner 制品地址必须对 Docker 和 K8s 都可达，当前 override 设置 `DATASCALPEL_FILE_STORAGE_RUNNER_ENDPOINT=http://192.168.5.102:9000`。Dispatcher 内部对象存储连接可保留原地址；不要把仅 Docker 可解析的 `host.docker.internal` 发给 K8s。

### 启停与升级

2026-09-27 补齐“恢复任务调度”，并在真实强制停用回归中修复排队取消漏发终态事件。在确认队列、活动任务及待清理资源为空后更新同一个 Dispatcher 容器。当前 JAR SHA256：`8ca9d91d92df353e7d3973beaa6f80bf614cddfa5c835bd9be77b8ffca1c15f1`。最终更新前的 Compose override 保存在 `/data/datascalpel-compute-engine/backups/20260927-lifecycle/compose.multi-target.yaml`，没有更换实例/引擎 ID、Topic 或 Runner r3。

先在 Admin 暂停两个引擎的新准入，等待/取消活动和排队任务，并确认外部资源清理完成，再操作 Dispatcher。Drain 不会自动执行完已有队列。必须沿用原 Compose project 名，避免创建第二套实例：

```bash
cd /data/datascalpel-compute-engine/compose
# 启动或应用配置
docker compose -p datascalpel-compute-engine --env-file .env -f compose.yaml -f compose.multi-target.yaml up -d
# 查看日志
docker logs --tail 100 datascalpel-compute-engine
# 已确认无活动任务后停止
docker compose -p datascalpel-compute-engine --env-file .env -f compose.yaml -f compose.multi-target.yaml stop
```

升级前配置、原容器说明、PG17 自定义格式账本备份位于 `/data/datascalpel-compute-engine/backups/20260926-multitarget/`，权限限制为 root。不要把包含凭据的目录整体提交仓库。旧 local Runner 和 r2 镜像保留作制品回退参考；多目标数据库不能直接交给旧单目标程序。完整控制面回退须先清空活动责任、核对新增运行记录，再恢复匹配的程序/配置/账本，不能直接覆盖升级后的业务历史。

### Admin 操作与 Kafka

计算引擎页面选择“连接 Dispatcher”，输入 `http://192.168.5.102:18092` 及部署 Token，发现目标后勾选注册。后续用“添加同实例其他目标”补充登记；重复选择已登记目标不会新增重复记录。删除前须停用引擎并解除任务引用，不会删除 K8s 集群本身。

“暂停任务调度”会保留原队列并暂停提交，运行中任务继续；“恢复任务调度”重新接收任务并继续原队列，不修改 Topic、不重启已有应用。“停用引擎（需无任务）”要求队列、活动执行及待清理资源为空；“取消全部任务并停用”会取消本引擎任务并等待清理，不撤销已写入数据。消息通道由系统分配，列表点击“查看全部 Topic”可查看并复制三类实际值，无需手填。

发现/登记使用 HTTP；任务 SUBMIT/CANCEL 仍由 Admin Outbox 发往各引擎独占 command Topic，Runner 发各自 runner-event Topic，Dispatcher Outbox 发共享 admin-event Topic。未重置旧 Docker 消费组或 Offset。任务绑定具体计算引擎，不根据任务类型猜测要投 Docker 还是 K8s。

本次 K8s 引擎实际为 **1 个在途应用、1 个提交槽、1 个 Executor**，Driver/Executor 各 1 Core / 1 GiB（另计 Spark 内存开销）。这是小规模联调配置，不是生产容量承诺。50 GiB 适用于临时程序对接，不适合长期保存多代镜像、大量输出或完整监控数据。K8s 目标未配置共享 Checkpoint，不开放持久化流式任务。
