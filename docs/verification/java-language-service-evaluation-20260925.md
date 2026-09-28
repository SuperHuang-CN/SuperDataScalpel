# 在线 Java 语言服务本机评估记录（2026-09-25）

状态：已完成本机短时微基准及特定隔离场景验证；不是生产容量认证，也不是功能已接入声明。

对应方案：[Spark JAR 在线 Java 补全与语言服务](../design/spark-jar-online-java-language-service.md)。

## 1. 环境与方法

- 本机 Windows，Intel Core Ultra 7 155H（16 核、22 逻辑处理器），约 64 GiB 内存；不是 102 服务器，也不是独占压测机器。
- Java 21.0.2；本机已安装 redhat.java 1.56.0 携带的 JDT LS core `1.61.0.202609021834`。
- Spark 4.1.1、当前已构建公开 SDK JAR、Scala 2.13.17 等共 21 个 classpath 条目。不连接业务数据源、不运行 Spark 作业。
- 主测参数 `-Xms128m -Xmx512m -XX:ActiveProcessorCount=4`；另有 1024 MiB 堆对照。ActiveProcessorCount 仅影响 JVM 看到的处理器数量，不是操作系统 CPU 硬限额。
- Node 脚本通过本地 stdio/LSP 驱动，无浏览器、网络、Admin、反代与 TaskEngine 转发开销。
- 单个 LSP 客户端向同一进程导入多个独立 Eclipse Java 项目；每个项目有独立 URI、相同包名/入口类名、用户独有方法和独有类型。此为多项目请求复用模拟，不是原生多客户端连接。
- 每个项目约 14 行代表性 SparkBatchJob 源码及一个独有类型；请求覆盖 `Dataset<Row>` 的 `df.se` 前缀补全与 SDK context 方法补全。
- 对 1、5、10、20 项目分别测试，每档 30 轮固定源码补全、30 轮修改源码后补全；每轮各项目同时请求，全部返回后等待 100 ms，共 2160 次被测请求。修改为方法内 revision 值，不代表大规模结构性编辑。
- 初始校准轮曾因无前缀候选被 100 项上限截断而导致预期项断言失败，修正为 `df.se` 后重测；校准轮不纳入最终正确性统计。

## 2. 结果

以下为 512 MiB 堆配置下“修改源码后补全”的结果。P95 为 95% 的该档请求不超过的耗时。驻留内存不等同于 Java 堆或容器总用量。

| 同进程项目数 / 并发请求数 | 请求数 | P50 | P95 | 最大耗时 | 阶段结束驻留内存 | 累计峰值驻留内存 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 30 | 27 ms | 32 ms | 35 ms | 855 MiB | 880 MiB |
| 5 | 150 | 70 ms | 123 ms | 128 ms | 881 MiB | 885 MiB |
| 10 | 300 | 119 ms | 227 ms | 246 ms | 883 MiB | 887 MiB |
| 20 | 600 | 221 ms | 415 ms | 460 ms | 890 MiB | 893 MiB |

- 主测候选列表均包含预期的 `select` 或 `models` 项，列表返回测试未记录错误；这**不包含**后续跨项目候选解析测试，后者明确失败。
- 新工作区从启动到首次 Spark 补全约 5.5 秒；两个隔离进程同时启动约 6.7 秒。操作系统文件缓存已热，不是冷机启动 SLA。
- 1024 MiB 堆对照中，修改后补全 P95 在 1/5/10/20 项目档分别为 36/129/232/426 ms，工作集约 0.9～1.0 GiB。
- 主测中途 jcmd 快照：堆已用约 296 MiB、已提交 512 MiB，Metaspace 已用约 68 MiB。不能将 512 MiB 堆上限当成进程总内存上限。

## 3. 共享进程隔离验证

复现步骤：

1. 项目 A 请求补全并获取候选项。
2. 立即执行 A 候选项的 `completionItem/resolve`，成功。
3. 项目 B 请求补全。
4. 再执行 A 原候选项的 resolve，返回 `-32603 / Invalid completion proposal`。

在 5、10、20 项目阶段重复复现，512/1024 MiB 两种堆配置均复现。检查源码及本机实际 JAR 的 javap 字节码，确认 `CompletionHandler.computeContentAssist()` 调用进程级 `CompletionResponses.clear()`。

其他检查：

- `this.` 成员补全正确区分各项目的独有方法，本项未观察到串扰。
- `workspace/symbol` 返回工作区内所有项目的独有类型，5 项目返回 5 个，20 项目返回 20 个。它是共享工作区搜索语义，不是用户隔离机制，也不据此认定 JDT LS 存在安全漏洞。
- 未实现共享进程多用户网关，也未验证主动解析候选、缓存隔离或过滤全局操作等改造是否能够解决所有问题。

## 4. 两个独立进程对照

- 各有独立工作区、配置缓存和 LSP 连接，堆上限各 512 MiB。
- 30 轮按照 A 补全 → B 补全 → A 解析 → B 解析交错执行，60 次解析全部成功。
- 搜索结果只包含各自工作区 URI。
- 向 A 注入 String 赋值给 int 的错误，A 收到准确类型诊断，B 未出现相应错误。
- 结束时工作集分别约 848、842 MiB；累计峰值各约 880 MiB；私有内存约 752、746 MiB。

本结果支持采用独立进程隔离测试中的两个编辑工作区；不代表已验证大量独立进程并发或长期运行稳定性。

## 5. 结论与未覆盖范围

真实 Java/Spark/SDK 补全在本测试中可用，但不能据此宣称“一个进程稳定服务 20 个独立生产用户”。共享进程的候选解析状态和全局工作区操作存在额外隔离要求，选择独立编辑工作区进程更符合当前成熟、易维护的目标。

每个活跃工作区约 1～1.5 GiB 可作为初始保守内存预算参考，不是固定资源承诺；TaskEngine 本身及其他服务另外计入。共享进程中 20 项目的内存不能当成 20 个独立进程的总内存。

未覆盖：目标服务器/Linux/容器、完整前后端和反代链路、大文件和复杂第三方依赖、所有 Java 语言特性、长时间内存泄漏、故障重启与源码恢复、多节点路由和节点下线。接口签名/悬停/导入等完整产品体验仍需接入验证。未运行现有系统功能回归，也未改变生产依赖和部署。

## 6. 原始记录位置与参考

原始评估材料保留于执行机器临时目录 `C:/Users/Lihuan/AppData/Local/Temp/datascalpel-jdt-eval-33f24efc/`：

- `bench.cjs`、`paired.cjs`：评估驱动，使用本机绝对路径，不是仓库自动化测试入口。
- `heap512/results.json`：主测统计与共享进程隔离结果。
- `verified/results.json`：1024 MiB 堆对照。
- `paired-results.json`、`isolatedA/`、`isolatedB/`：两个独立进程对照与日志。
- `first/`：不计入最终统计的校准轮。

临时目录可能被系统清理；本文件归档了结论、主要测量值、测试方法及边界，但未将生成的 Eclipse 缓存、插件二进制或全部原始日志纳入仓库。评估结束时子进程均已退出。

参考：[JDT LS 运行方式](https://github.com/eclipse-jdtls/eclipse.jdt.ls#running-from-the-command-line)、[CompletionHandler](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/handlers/CompletionHandler.java)、[CompletionResponses](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/main/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/handlers/CompletionResponses.java)。线上 main 会变化，本次结论以标明版本的本机实测为准。
