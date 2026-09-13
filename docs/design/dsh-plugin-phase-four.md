# 第四阶段第一批：全局助手抽屉与会话管理

状态：第一批已实现并完成下述针对性及真实环境验证。适用版本：插件 `@datascalpel/dsh-plugin@0.4.0`、DSH `0.1.5-rc.1`。

后续 `0.5.0` 已扩展聊天附件、截图和历史卡片，详见[聊天附件](dsh-chat-attachments.md)；以下第一批验证记录不代表附件已经完成相同运行验收。

## 范围

在顶部栏提供全局 AI 助手。无模态右侧抽屉默认使用 40% 窄模式（最小 520px），可切换至 80% 宽模式，720px 及以下窗口占满；业务页面保持可操作，路由切换保留当前会话和输入草稿。关闭抽屉只断开事件订阅，不停止模型执行。

本批提供新建、标题查询、重命名、归档和恢复，以及文本对话、Markdown、工具结果、原生追问和停止执行。使用 React、Ant Design、TanStack Query、react-markdown 与 remark-gfm。业务上下文和业务结果跳转属于第二批，尚未实现。

当前 DSH 公开 SessionPersistence 和 SessionController 没有永久删除接口，因此本批明确使用“归档”，保留原生历史及工作文件。归档不是永久删除；不修改 DSH 核心或自行删除其底层存储文件。

## 界面与用户状态

- 模块为 `data-scalpel-ui/src/modules/dsh`，由 AppShell 首次点击时懒加载。用户 UUID 作为查询缓存及组件身份；退出登录销毁输入、选择和缓存。
- 开启时先检查 capabilities，再自动初始化个人工作区；不开启不初始化，开启不自动创建空会话。
- 宽模式以左侧 280px 常驻 Panel 管理会话，右侧持续显示当前对话；窄模式以临时左侧 Panel 覆盖部分对话区，选择会话后收起。打开会话 Panel 不断开当前会话事件订阅。宽窄偏好按登录用户保存在浏览器本地。列表按最近对话活动倒序，提供未归档／已归档筛选、标题查询和每页 20 条分页；可见时 15 秒刷新、窗口重新获得焦点刷新。
- 普通消息使用 Enter 发送、Shift+Enter 换行，输入法组合输入不触发发送。执行中保留草稿但拒绝普通消息，仍可追问回复或停止。
- 显式归档确认保留历史与文件。执行／追问／取消中拒绝归档，需先停止后再次归档。恢复只更改归档状态，不恢复 Agent。
- Markdown 不执行原始 HTML，不加载图片，限制链接协议。代码可复制，代码和表格在消息内部滚动。工具参数和结果折叠显示；HTTP 失败、工具失败、执行结果不确定、响应读取不完整分别显示。
- 草稿仅在当前浏览器内存中保存；关闭及切换页面保留，刷新或退出清除。服务端历史持续保留。

## API 与持久化

Admin `/api/v1/dsh` 与 Bridge `/bridge/v3/users/{userUuid}` 新增／扩展：

| 地址 | 契约 |
| --- | --- |
| `GET /sessions` | `query` 最多 100 字符；`archived` 默认 false；沿用 offset/limit，默认 20、最多 100 |
| `POST /sessions/{id}/actions/update` | `{ "title": "会话标题" }`，去除首尾空白，1～100 字符 |
| `POST /sessions/{id}/actions/archive` | 无业务参数；忙碌 409；成功保存归档并释放 Agent |
| `POST /sessions/{id}/actions/restore` | 无业务参数；恢复后仍为冷会话 |
| `GET /sessions/{id}/messages?mode=cursor` | 最近 50 条，`beforeSeq` 读取该原生序号以前的消息，`limit` 最多 200 |

列表及详情新增 `title`、`archived`、`lastActivityAt`。游标响应含 `items`、`hasMore`、`nextBeforeSeq`、`historyThroughSeq`，items 按原生消息顺序返回。原来的 offset/limit 历史模式保留；不得与 cursor 模式混用。新能力只开放给 v3，v1/v2 保留原契约。

标题、手动标题标记、归档和最近活动存入现有插件控制记录，不新增 Admin 聊天数据库。新字段可选，旧记录首次打开用户域时从原生历史补齐；后续列表读取控制记录及活动 Agent 状态，不恢复 Agent、不重复扫描全部历史。

默认标题“新对话”，首条已接收用户消息取归一化空白后的前 40 个字符，不增加模型调用。用户手动标题不被后续消息覆盖。此标题是 DataScalpel 展示元数据，不同步原生页面标题。

活动时间在消息接收、追问回复和轮次结束时更新，始终不回退。查询、重命名及归档恢复不更新活动时间。更改使用会话级串行控制及持久化确认。已归档会话拒绝发送、恢复 Agent 和追问回答，状态及历史仍可读取。

当前用户响应增加可空 `userId` 字段，来源于现有 JWT UUID 声明；旧 JWT 原业务行为不变，DSH 仍要求重新登录。所有接口保留当前用户归属检查，跨用户 404，继续排除在系统 MCP 目录外。

## 事件、失败与恢复

- 继续采用 DSH WebSocket → Admin SSE → 浏览器 fetch 流，Bearer 仅在认证头，不传浏览器直连地址或托管秘密。
- `session.updated` 推送展示元数据变化。每次建立连接先处理 `session.snapshot`，读取最新持久历史，再处理缓冲事件；按消息 ID、原生序号和连接序号去重。
- 临时回答随 attempt 区分；完整消息取代临时回答，重连不重放临时增量。若缓存与最新历史相隔过大，重新展示最近一页并允许向前加载，避免伪造连续历史。
- 网络失败按 1/2/5/10 秒退避；55 秒连接建立超时、45 秒无心跳或数据超时。用户失效和 404 停止重连。普通命令使用 55 秒 HTTP 超时，已经建立的流不受普通请求超时约束。
- 不自动重试命令；创建和消息保留客户端 UUID。超时显示结果待确认，先读取状态及历史；明确未发送失败允许修正后发送。
- 当前打开的会话只保持一条订阅，关闭、切换、退出释放。SSE 缓冲限制 1 MiB；慢消费者或序号缺口重新同步。
- 每用户 4、全局 8 活跃 Agent，空闲 15 分钟释放等原有约束保持。新 UI 不自动修改 MCP 开放清单、权限、模型配置或工具集合。

## 开发与运行验证

针对性插件、前端和后端测试覆盖元数据兼容、归档执行边界、游标历史、查询编码、SSE 分片、认证、消息合并及失败展示。实际环境脚本：

```bash
python3 integrations/dsh-plugin/scripts/verify-phase-four.py initial
# 使用原部署方式重启 Admin 和 DSH 后：
python3 integrations/dsh-plugin/scripts/verify-phase-four.py resume
```

脚本复用第三阶段测试账号，在 `.local/dsh-phase-four/` 保存会话标识；不输出凭据，不修改业务数据。浏览器验收使用现有开发环境，DataScalpel 的任何启动与重启只能通过根 `./start-local-dev.sh`。

实际结果见[第四阶段第一批验证记录](dsh-plugin-phase-four-verification.md)。第一批完成不代表业务页面上下文及结果跳转已完成。
