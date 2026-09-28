# Java 固定依赖索引复用：实现与验证

日期：2026-09-27。范围：TaskEngine 管理的 JDT LS；不涉及 Dispatcher、Runner、任务配置或数据库。

## 实现

- TaskEngine 后台使用同一工作区实现启动临时索引生成者，不复制生产会话 metadata，不执行用户代码。
- 完整导出后停止生成者，校验全部外部 JAR/JDK 索引，原子发布带清单的新快照目录；文件锁串行生成。
- 指纹包含 JDK modules/release/jrt、JDT 插件及配置、分析依赖内容/路径/时间。支持检测版本字符串未变、大小/时间未变但内容变更的依赖。
- 每个会话普通复制索引到私有目录并校验，JDT 不接触公共快照，不使用硬链接。依赖不匹配、缓存损坏或复制失败时回退普通启动。
- 默认开启，可通过运行时环境变量关闭或指定缓存目录。无需新增服务、依赖或管理接口。
- 不改变当前页面刷新创建新会话的规则，也没有去掉编辑器语义就绪检查。

本轮采用“预建快照 + 私有副本”，而不是前期实验中的“多个进程直接读取同一个索引目录”。原因是 JDT LS 自身存在回写共享索引的行为；普通副本可以在 Windows/Linux 上保持写入隔离，不依赖只读属性或 ACL 的特殊行为。代价是每个活跃会话约 115 MiB 的额外磁盘占用。

## 实测

Windows 开发机，JDK 21.0.2、JDT LS 1.60.0；512 MiB 最大堆、2 个可见处理器，与当前工作区参数相同。以下为真实 JDT 子进程组件测试，不是完整浏览器页面打开时间。

| 测试 | 语义就绪 |
| --- | ---: |
| 前期基线：新工作区重复建索引 | 15.598 秒 |
| 前期原理验证：直接复用已发布索引 | 5.798 / 5.940 秒 |
| 本轮接入：含依赖指纹校验、复制与校验 | 首轮 7.033 / 6.689 秒；复测 6.553 / 6.734 秒 |

接入计时从工作区创建开始，包含实际 initialize、JDK String 构造器与 SDK models 补全；预生成不计入消费会话耗时。首次无缓存仍需生成，提前启动 TaskEngine 才能让用户首次进入时受益。缓存尚未完成不等待生成，继续原流程。样本不是生产 SLA。

## 验证方式

使用根 Maven Wrapper、既有 settings-superhuang.xml、JDK 21，显式启用已安装的真实 JDT LS；未启动额外应用环境、Mock 或数据任务。

```powershell
.\mvnw.cmd -pl data-scalpel-task-engine -am `
  '-Dtest=JavaLanguageIndexCacheTest,JavaLanguageServerTest,JavaLanguageUriRewriteTest,LspFramesTest,JavaLanguageWorkspaceIntegrationTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Ddatascalpel.test.jdtls.home=F:/workspace/freedo/SuperDataScalpel/.local/jdtls/1.60.0' test
```

覆盖：

- 同时间/大小不同内容、路径变更及 SDK 类目录新增文件的指纹失效。
- 两份普通副本相互独立，修改一份不影响发布源或另一份；损坏校验与非法路径拒绝。
- 缓存开关和目录配置。
- 从空缓存真实生成、两个新工作区使用、公共快照不变、新管理器加载已有缓存，以及损坏发布文件后返回普通索引路径。
- 原有内部连接鉴权、容量、重复连接、断连保留、URI 转换与 LSP 帧处理。
- 真实语言服务的重命名、撤销后文件复用、类型/SDK 补全、候选解析、诊断、重连及异常进程回收。

最终结果：上述 17 项测试全部通过（失败 0、错误 0、跳过 0），Maven Reactor 构建成功。真实 JDT 集成类共 6 项，不使用假服务代替。

最初一次构建因终端默认 JDK 17 而在已有 SDK 文档生成阶段失败；改用工程要求的 JDK 21 后正常构建。接入测试初版误将补全前缀传为 `String(`，而既有助手会自动追加括号；修正测试参数后首轮 12 项通过，不涉及生产补全行为修改。

## 生效与边界

- 现有 IDE 的 Admin、TaskEngine 和原用户语言会话未被停止或重启；本次编译不代表运行中的 TaskEngine 已加载新代码。
- 更新并重启 TaskEngine 后自动准备索引，无需重启 Admin/Dispatcher/Runner。后台最多多一个临时语言进程，部署需预留内存。
- 未做完整浏览器页面耗时回归，也未在 Linux 服务器运行本实现；未宣称已部署到 102。
- 缓存旧版本不在线自动删除；停节点后按 [运行时维护说明](../../data-scalpel-task-engine/src/main/distribution/JAVA-LANGUAGE.md)清理归属明确的旧快照及未完成目录。

设计与配置见 [语言服务方案](../design/spark-jar-online-java-language-service.md#固定依赖预建索引2026-09-27)。
