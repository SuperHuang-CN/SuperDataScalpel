# 第三阶段实际验证记录

日期：2026-09-11。范围：Admin 用户接入、工程内插件 `0.3.0`、旧助手移除；DSH 核心固定 `0.1.5-rc.1`。不是第四阶段业务助手页面验收。

## 环境与交付

- 使用现有 DSH Docker（13080）和第二阶段独立验证台（13081）；没有创建第二套 DSH 或数据库。
- DataScalpel 通过根 `./start-local-dev.sh` 启动及重启，Admin 18080，前端 `http://localhost:18887`。前端监听本机 IPv6 localhost，不能把其地址一律替换为 127.0.0.1。
- 真实用户入口 `/api/v1/dsh` 已启用，独立 Bridge 与加密密钥保存在不提交的本地配置。
- 插件源码、Preset、构建说明和验证脚本位于 `integrations/dsh-plugin`；未修改 DSH 核心。
- 两个专用验证账号 `dsh_verify_alice`、`dsh_verify_bob` 使用独立角色，密码与会话标识仅保存在 `.local/dsh-phase-three/verify-state.json`（0600）。Alice 有数据源只读权限，Bob 没有该权限。

## 已执行与结果

| 验证项 | 实际结果 |
| --- | --- |
| 旧助手接口与源码 | OpenAPI 不再注册 `/api/v1/assistant`、`/api/v1/system/llm-models`；旧 Business、前端模块及专属状态删除 |
| 数据库备份与删除 | 六表结构/数据以 pg_dump custom 格式备份，验证清单与 SHA-256 后单事务 DROP RESTRICT；重复检查不存在，重启后未重建 |
| 并发初始化 | 两用户各并发 3 次 ensure，各只有一个工作区、一个托管令牌；重复访问复用 |
| 创建去重 | 同 clientSessionId 得到同一 sessionId；同消息 ID/内容返回 duplicate，不再执行 |
| 跨用户访问 | 状态、历史、事件、发消息、恢复与取消均返回 404 |
| 托管令牌保护 | update、enable、disable、rotate、delete 五类手工操作全部返回 409 `SYSTEM_MCP_TOKEN_MANAGED` |
| 手动令牌回归 | 专用手动令牌创建、修改、启停、轮换、删除正常，结束后删除该临时令牌 |
| 真实模型与 MCP | 两用户并发对话完成。审计确认 Alice `GET /api/v1/data-sources` HTTP 200/RESPONDED；Bob api_describe 返回 ACCESS_DENIED、403/NOT_DISPATCHED |
| 动态角色变更 | 仅修改 Alice 验证角色；原有会话 MCP 客户端立即反映权限调整，测试结束恢复权限 |
| 接口开放变更 | 临时关闭之前已经开放的数据源列表接口，旧契约下直接调用仍被拦截、NOT_DISPATCHED；随后恢复原开放状态，没有新增开放接口 |
| 原生追问 | 两用户并发追问、自由文本回答、回答去重及过期回复拒绝通过 |
| 独立取消 | 取消 Bob 不影响 Alice 完成；旧追问返回 410 |
| SSE | 收到文本增量、会话关联事件及快照；断开观察者后任务继续，重新连接读到状态与最终历史 |
| 用户停用 | 新 DSH 命令立即 401；最终实测事件连接 2.53 秒关闭并送达 connection.failed；Agent 范围 2.74 秒撤销，旧问题失效 |
| 用户重新启用 | 复用原工作区及凭据；托管启用状态随当前用户状态同步，不重新签发秘密 |
| 同名重建 | 专用临时用户删除后旧 JWT 被 DSH 拒绝；同名新用户得到不同 UUID/工作区，旧会话返回 404，结束后删除临时新用户 |
| 旧 JWT | 在内存中构造有效签名、缺 userId 的旧式 JWT；原数据源 API 仍可用，DSH 返回 DSH_RELOGIN_REQUIRED |
| 请求上限 | 大于 1 MiB 的 Admin 消息请求返回 413，未进入模型命令 |
| DSH 重建恢复 | 两用户均能恢复原持久会话并准确回忆先前随机标记 |
| Admin 独立重启 | 经根启动脚本重启，两个用户继续恢复上下文，绑定与令牌不重复产生 |
| 前两阶段保留 | v1 能力就绪且保留 7 个会话；v2 Alice/Bob 各原有会话保留、能力就绪；独立验证台 health=200 |
| 页面回归 | 数据源列表、新建表单与必填校验正常；现有任务详情与普通编辑表单正常；顶部助手及 AI 模型菜单已消失；托管令牌显示标签且无操作，手动令牌保留操作 |

## 构建与针对性测试

- 插件 TypeScript 构建与 Node 测试：21 项通过。包含原有 18 项，以及全局槽位、授权过期/撤销和真实用户控制存储隔离。
- 前端 `pnpm --dir data-scalpel-ui build` 通过（TypeScript + Vite）。保留原有部分动态导入和大 chunk 提示。
- 后端 Maven Wrapper 编译及启动通过。全模块 `testCompile` 被既有 ComputeEngine、DispatcherRegistrationRequest 和 Kong Gateway 测试的旧签名阻挡，没有改动这些无关测试。
- 使用 Wrapper/settings 生成测试类路径，单独编译本次相关测试，再通过 Maven Surefire 执行 DshIdentityTest、DshManagedTokenTest、DshLeaseReconciliationTest、SystemMcpTokenTest，7 项通过；验证 AES-GCM 用户绑定/损坏失败、UUID 身份、全部托管管理入口、当前用户权限及托管生命周期同步。

实际脚本：

```bash
python3 integrations/dsh-plugin/scripts/verify-admin.py ready
python3 integrations/dsh-plugin/scripts/verify-admin.py setup
python3 integrations/dsh-plugin/scripts/verify-admin.py model
python3 integrations/dsh-plugin/scripts/verify-admin-lifecycle.py
python3 integrations/dsh-plugin/scripts/verify-admin-boundaries.py
python3 integrations/dsh-plugin/scripts/verify-admin-legacy-jwt.py
# 重启完成并确认健康后：
python3 integrations/dsh-plugin/scripts/verify-admin.py resume
```

这些是当前开发环境的变更测试，涉及专用验证账号和角色；不要直接面向生产运行。不要同时执行 setup 与权限变更脚本。

## 备份与保留事项

备份位于 `.local/dsh-phase-three/legacy-backup/legacy-assistant.dump`，元信息位于同目录 `metadata.json`。原本地配置（含旧模型解密密钥）保存在受保护的 `application-local.before-dsh.yml`；旧源文件副本在 `.local/dsh-phase-three/legacy-source.tar.gz`。均不提交，报告不包含密钥或正文。

备份数量：旧会话 1、消息 9、运行 6、工具调用 18、变更单 0、模型配置 1。删除仅限用户批准的六张旧表，未使用 CASCADE。系统 MCP 目录可能保留已删除路由的 REMOVED 历史投影，不能调用，不能将该投影误认为旧 Controller 仍在运行。

## 验证边界

没有新增业务助手页面、业务上下文接入、Shell 或 Skills；第三阶段 Preset 只含追问、受控个人文件和三个系统 MCP 工具。原生工作环境、模型配置和用户已有文件保持持久化。

本轮没有进行长时间负载测试、真实断网/数据库故障注入或完整业务模块回归。缓冲上限、授权租期和错误边界有代码及针对性测试覆盖；不能将本记录当作所有故障条件均已实测。多用户边界针对可信用户及受控工具，不承诺抵抗容器 root 或管理员直接操作底层文件。

启动观察：最后一轮冷启动约 98 秒；曾在 MCP 目录尚未就绪时提前运行验证，作用域 MCP 客户端加载被拒绝、未提交模型消息。应等待应用健康和接口目录就绪后运行脚本。删除用户的托管令牌最终均显示停用，原手动令牌未被同步逻辑修改。

收尾复核：备份通过 `pg_restore --file=/dev/null` 完整解码（未启动数据库或恢复到业务库）；旧六表仍不存在，DSH 与独立验证台容器均为 healthy。生命周期脚本分别检查事件关闭与 Agent 撤销，避免把事件断开误认为 Agent 已结束。

收尾发现并修正：SSE 检查到用户停用后，该轮授权协调保留撤销快照，避免用户快速重新启用使活跃 Agent 的取消被漏过。验证脚本等待用户 Agent 范围从 Bridge 撤销后再重新启用，并分别计时。

针对性补充：快速重新启用的撤销快照回归，以及选项答案省略空 custom 字段的序列化回归均通过。收尾启动曾被同时修改的数据填报代码暂时不一致和 Task Runner 制品过期阻挡；未改动填报代码，按根脚本提示用 Wrapper 重新打包 Runner 后继续启动。

最终复测：独立生命周期会话通过并发选项追问、自由文本回复、回答去重、独立取消、SSE 重连、用户停用与重新启用；事件关闭 2.53 秒、Agent 范围撤销 2.74 秒。生命周期脚本每次新建独立验证会话，避免反复取消留下的对话历史影响模型后续是否再次追问；原上下文恢复用会话保留。
