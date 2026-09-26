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

内部 WebSocket 端口固定为 TaskEngine HTTP 端口 **+100**。例如 HTTP 8091 对应 8191。
只需允许 Admin 访问内部端口；浏览器通过 Admin 全程转发，不获取内部 Token，不要求增加反向代理。
预留单工作区约 1～1.5 GiB 总内存，再按目标机器实际负载调整会话上限；不要只按堆大小计算可承载人数。

## 运维边界

断连超过保留期、初始化超时及受管进程异常会回收该工作区。TaskEngine 正常退出时结束子进程。
异常断电后的磁盘残留不是源码存储；维护清理必须先停节点并确认目录归属，不删除其他节点或活跃会话目录。
扩容到多节点还需要会话路由策略，不可让同一编辑会话的消息随机落在不同节点。
