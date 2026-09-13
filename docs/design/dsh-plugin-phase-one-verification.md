# DSH 插件第一阶段验证记录

状态：第一阶段已完成。验证日期：2026-09-11。范围为单个可信操作者通过内部 HTTP 与 WebSocket 使用独立验证 Preset；不代表已支持多用户。

## 环境与交付

| 项目 | 实际值 |
| --- | --- |
| DSH | `@deepseek-ai/dsh@0.1.5-rc.1` |
| 插件 | `@datascalpel/dsh-plugin@0.1.0` |
| 运行环境 | 现有 `datascalpel-dsh` Compose 项目，服务 `dsh`，Node.js 24 |
| 原生页面 | `http://127.0.0.1:13080/` |
| 内部 HTTP | `http://127.0.0.1:13080/bridge/v1` |
| 验证工作区 | `/workspace/bridge-phase-one` |
| 验证 Preset | `datascalpel-bridge-phase-one`；基础对话、原生上下文压缩及 `ask_user_question` |
| 模型引用 | `deepseek-official` / `deepseek-flash`，复用已配置提供方，不复制 API Key |
| 主验收会话 | `7f5eafa8-1443-42e1-b9ca-0899953ca109` |
| 对话验收完成时间 | `2026-09-11T07:44:58Z` |
| 恢复验收完成时间 | `2026-09-11T07:46:45Z` |

源码、锁文件、Preset、测试和脚本均在 [integrations/dsh-plugin](../../integrations/dsh-plugin/README.md) 中维护。镜像从仓库源码构建，安装产物与 DSH 共用依赖树；`check-runtime.mjs` 校验直接依赖与 peer dependencies 的实际版本。未修改 DSH 核心包，未修改 DataScalpel 业务代码。

最终交付镜像 ID 为 `sha256:14868ae0c350255c51f93d878624a24f6b7e28915a5f4136ee5d1bf842222fd4`。容器内 26 个插件文件与仓库构建产物逐文件摘要一致，共享依赖校验通过，容器状态为 `running / healthy`。最终部署在 `2026-09-11T07:54:42Z` 再次通过能力探测、冷历史读取（17 条）和显式恢复（UNLOADED → IDLE）。

## 真实运行结果

执行 `scripts/verify.mjs conversation --new-run`，以一个已存在的原生会话 ID 作为越界检查目标；脚本结束在重启检查点。随后对现有服务执行 `docker compose -f deploy/dsh/compose.yml restart dsh`，再执行 `scripts/verify.mjs resume`。两个阶段最终均以退出码 0 结束。

| 场景 | 结果 | 证据 |
| --- | --- | --- |
| 加载及能力探测 | 通过 | HTTP 200，返回插件及 DSH 实际版本，组件就绪 |
| 独立认证 | 通过 | 缺失/错误 Bearer 的 HTTP 请求与未认证 WS 握手拒绝；URL 密钥参数拒绝 |
| 固定工作区 | 通过 | 重复和并发 ensure 返回同一工作区 |
| 会话创建 | 通过 | 同一 clientSessionId 复用原会话，首次 201、重复 200 |
| 真实基础对话 | 通过 | 收到 assistant.delta、最终回答、run.completed，持久历史含随机标记 |
| 消息去重和冲突 | 通过 | 相同 ID/文本只执行一次；同 ID 不同文本 409 |
| 原生追问 | 通过 | 模型实际调用 ask_user_question，收到结构化交互；选择选项后继续回答 |
| 自由文本追问 | 通过 | 通过原生答案结构提交自由文本后继续对话 |
| 忙时控制 | 通过 | 等待追问时新的普通消息被 409 拒绝，回复与取消仍可用 |
| 取消及交互失效 | 通过 | 等待追问的运行被取消，收到 run.cancelled，最终 IDLE/CANCELLED；旧回复 410 |
| 断线重连 | 通过 | 执行中关闭 WS 不取消 Agent；新连接和历史接口恢复最终结果 |
| 会话归属 | 通过 | 对实际原生会话 `session-6a9ebd88-0edf-4e75-8114-ce273cd22f95` 的读取、历史、恢复及取消均返回 404 |
| 容量限制 | 通过 | 4 个活跃 Agent 后，新增会话返回 503 BRIDGE_CAPACITY_EXCEEDED |
| 冷历史与显式恢复 | 通过 | 重启后先读取 UNLOADED 状态及一致历史；resume 后 IDLE，重复 resume 复用 |
| 真实上下文恢复 | 通过 | 模型在新轮次准确回答重启前的完整随机标记 |
| 中断恢复 | 通过 | 旧交互 410；恢复后无新的模型流、用户/助手消息或追问，状态保持 IDLE |

原生恢复会补写 `interrupted-tool-result-*`，闭合被中断的工具调用。验收允许这类原生修复记录，并分别检查模型未被自动调用，不能把修复记录当作任务重放。

脚本的本地检查点和机器结果位于插件目录 `.verification-state.json`、`verification-conversation.json`、`verification-resume.json`，均不提交。本文保留可共享的结果、版本和会话标识，不包含访问秘密、模型凭据或完整聊天正文。

## 针对性测试

`npm test` 包含 TypeScript 编译和 Node.js 测试；最终 16 项通过，0 项失败。覆盖：

- Bearer、Cookie、重复认证头、浏览器 Origin，以及请求长度、非 JSON 和不完整 JSON。
- 历史排除压缩替换及内部请求头；正在执行与重启后中断结果的区分。
- 并发消息去重、内容冲突、会话忙、持久化失败时不误报接收成功或再次入队。
- 原生追问的选项与自由文本校验、重复回复、取消与失效。
- WS 快照竞态、按历史边界接续、会话范围、慢消费者关闭和下行连接拒绝命令。
- 缺失配置和存储失败明确报错；冷历史只打开原生只读句柄，不创建 Agent。
- 使用实际 Cordis 作用域验证 Bridge 回答器只优先处理自己的 Agent，释放后原生回答器仍可用。

这些自动化测试使用受控存储/连接替身验证边界；上表的模型对话、追问与重启是在现有 Docker 服务中实际执行，二者不互相替代。

## 原生环境保留

部署前保存了 875 个原生配置、Preset、Skills 及工作文件的摘要。重启后比较，其中 12 个变化项全部位于 `.npm-cache` 的缓存索引或旧日志，其余 863 项保持一致。模型及 MCP 配置、Skills 和用户工作文件未发现改写。摘要只在工程 `.local/dsh-phase-one/` 中保存，不记录文件正文。

原生页面可以打开；旧会话“你能用么”的 7 轮历史仍可读取，原有模型设置面板仍能打开。未发起原生业务工具测试：本阶段按约定不调用 MCP、Skills 或 DataScalpel 业务接口，配置保留不等同于重新验证其业务行为。

DSH 继续使用原端口、数据卷、root 与既有 SYS_ADMIN 配置；本阶段未替换沙箱策略、清理数据卷或启动另一套 DataScalpel 应用。服务在验收后保持运行。

## 已修正的问题与阶段限制

- Cordis 从用户 Profile 解析包名时无法找到镜像内插件，部署补丁改用已安装入口的 file URL。
- 存储域名称使用 DSH 支持的 `datascalpel_bridge`；Preset 通过插件已声明的服务调用并挂载到 Agent，避免未声明依赖的上下文访问。
- 原生页面回答器可能先接收追问，使用公开 prepend 选项及精确 Agent 作用域接管自己的交互，不修改全局原生处理器。
- 对话历史只投影原生追加消息，排除压缩替换；完整回答和完成事实确认持久化后才发布。
- 恢复有原生中断结果修复行为；旧交互不重建、遗留 inbox 不重放。临时文本增量不支持跨重启重放。
- 原生压缩服务已装配并随验证 Agent 正常加载，未刻意耗尽模型上下文去触发超长对话压缩。本阶段只验收基础对话；长上下文压力、异常断电/磁盘损坏注入不在本次真实运行结果内。
- 只有一个可信验证操作者；Bridge 密钥不是用户级授权。用户隔离、MCP 身份适配、Admin 接入、页面、文件接口与审批桥接未实现，也未作为后续最终方案定稿。

下一步按[路线图](dsh-integration-roadmap.md)讨论第二阶段，依据本阶段已验证的扩展机制及限制设计多用户边界。
