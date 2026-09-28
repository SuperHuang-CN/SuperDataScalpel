# 在线 Java 语义提示运行时

语言服务由本 TaskEngine 启动和回收，不另部署服务；每个编辑会话一个 JDT LS 子进程。
现有画布和编译接口不变。未安装语言运行时时，编辑器显示不可用并保留基础资源提示、保存和编译检查。

## 离线准备

在有网络的交付环境获取并校验官方归档，再连同 TaskEngine 拷贝到内网：

- Eclipse JDT LS **1.60.0**，需要 JDK 21。
- 归档：<https://download.eclipse.org/jdtls/milestones/1.60.0/jdt-language-server-1.60.0-202606262232.tar.gz>
- SHA-256：`e94c303d8198f977930803582738771fd18c52c5492878410bf222b1aa81ef1d`。
- 校验后解压到 TaskEngine 的 `language-server/`，其下直接包含 `plugins/` 和 `config_linux/` 等目录；保留归档原有许可、NOTICE 等文件。
- 也可通过 `DATASCALPEL_JDTLS_HOME` 指定绝对目录。IDE 启动 main 时设置此变量即可；不要求安装 VS Code。
- 不把 Eclipse JAR 放入 TaskEngine `lib/`，不让用户工程指定 JVM 参数或分析依赖，不在首次编辑时联网下载。

## 配置

| 环境变量 | 默认 | 说明 |
| --- | --- | --- |
| `DATASCALPEL_JDTLS_HOME` | 未设置 | 官方运行时根目录；启动脚本会识别同包的 language-server |
| `DATASCALPEL_LANGUAGE_WORK_ROOT` | 系统临时目录/datascalpel-language | 当前节点可写、仅服务账号可访问的分析工作区；不要指向业务数据目录 |
| `DATASCALPEL_LANGUAGE_MAX_SESSIONS` | 4 | 活跃及断连保留的工作区总上限，1～64 |
| `DATASCALPEL_LANGUAGE_HEAP_MIB` | 512 | 每个子进程最大 Java 堆，256～4096；不是总进程内存 |
| `DATASCALPEL_LANGUAGE_RETENTION_SECONDS` | 180 | 断连保留秒数，5～1800 |
| `DATASCALPEL_LANGUAGE_INDEX_CACHE_ENABLED` | true | 后台预生成固定依赖索引；false 恢复原有每会话独立索引 |
| `DATASCALPEL_LANGUAGE_INDEX_CACHE_ROOT` | 工作区根目录/index-cache | 当前节点的索引快照目录；可配置持久化目录，限服务账号访问 |

内部 WebSocket 端口固定为 TaskEngine HTTP 端口 **+100**。例如 HTTP 8091 对应 8191。
只需允许 Admin 访问内部端口；浏览器通过 Admin 全程转发，不获取内部 Token，不要求增加反向代理。
预留单工作区约 1～1.5 GiB 总内存，再按目标机器实际负载调整会话上限；不要只按堆大小计算可承载人数。

## 固定依赖索引

TaskEngine 启动后在后台检查缓存；首次缺失时额外启动一个临时 JDT LS，使用平台固定的分析 classpath 生成 JDK/JAR 索引，完成后关闭，不执行用户代码，也不创建任务。准备阶段最多额外占用一个同堆配置的语言进程，需预留内存；它不占用编辑会话额度。单次准备限时 120 秒，失败最多每 5 分钟重试，编辑会话不等待准备，直接回退原有独立索引流程。

快照按 JDK（包含 lib/modules）、JDT 插件/配置、分析依赖内容、路径与修改时间计算 SHA-256 指纹；同版本 SNAPSHOT 内容变化也会失效。发布前检查完整索引集、依赖时间戳及内容稳定性，关闭生成进程后原子发布。文件锁避免同目录多个 TaskEngine 同时生成。不把正在生成的目录当缓存使用。

**各会话使用经过校验的普通文件副本，不共享可写索引。** JDT LS 的 sharedIndexLocation 有回写行为，故不直接传入公共快照目录，也不使用硬链接或复制整个 Eclipse metadata。每次新建会话校验当前依赖指纹、复制并验证快照；失败不传索引路径，继续正常启动。用户源码和源码索引一直在各会话私有工作区内，目录形式的 SDK classes 仍独立分析。

当前分析依赖的快照约 115 MiB，每个活跃会话额外占用约同等磁盘空间，副本随会话回收。新依赖会生成新快照；旧版本不自动删除，停节点后可清理本节点 index-cache 下已确认归属的旧 snapshot-* 和 building-* 目录。不要在运行中清理正在复制/发布的缓存，也不要将此目录配置到业务数据目录。

只需更新并重启 TaskEngine 加载本改动，不需要重启 Admin、Dispatcher 或任务 Runner。首次准备完成以日志 `Java language index snapshot ready` 为准；重启加载已有快照显示 `snapshot loaded`。这减少重复索引成本，不消除 JVM 初始化成本，也不改变编辑器真实就绪探测。

## 运维边界

断连超过保留期、初始化超时及受管进程异常会回收该工作区。TaskEngine 正常退出时结束子进程。
异常断电后的磁盘残留不是源码存储；维护清理必须先停节点并确认目录归属，不删除其他节点或活跃会话目录。
扩容到多节点还需要会话路由策略，不可让同一编辑会话的消息随机落在不同节点。
