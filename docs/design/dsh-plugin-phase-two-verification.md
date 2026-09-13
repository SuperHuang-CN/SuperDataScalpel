# DSH 第二阶段验证记录

执行日期：2026-09-11。范围为独立验证台和现有共享 DSH Docker；没有启动或调用 DataScalpel 应用。

## 运行版本与入口

| 项目 | 实际值 |
| --- | --- |
| DSH | `@deepseek-ai/dsh@0.1.5-rc.1`，核心未修改 |
| 插件 | `@datascalpel/dsh-plugin@0.2.0` |
| 验证台 | `@datascalpel/dsh-playground@0.1.0` |
| 原生页面 | `http://127.0.0.1:13080` |
| 独立验证台 | `http://127.0.0.1:13081` |
| 测试 MCP | Docker 内部 `http://playground:13082/mcp`，未发布宿主机端口 |
| 模型 | 复用原生 `deepseek-official` / `deepseek-flash`，未复制模型凭据 |
| DSH 镜像 ID | `sha256:6776f9023d01d3ee3e65e055133cbb2575a2af6e2394f327d77ca39c5423f503` |
| 验证台镜像 ID | `sha256:ff4c43df9344bcbda275189f0850ee1774e34e6886a3483d1d7f3d9060889050` |

两个容器均已运行且健康。构建前 Docker 文件系统约剩余 21 GiB，本次未清理任何缓存、镜像、容器或数据卷。插件打包安装到 Host 共享依赖树，构建时运行依赖版本检查通过，没有第二份 DSH 运行时。

## 自动检查

`npm test --prefix integrations/dsh-plugin`：18 项通过，包括原阶段一的 16 项与新增文件/用户存储隔离检查。`npm test --prefix integrations/dsh-playground`：2 项通过。

新增检查证明：

- Alice/Bob 相同文件名得到不同内容；目录嵌套读写正常。
- 绝对路径、父级跳转、符号链接、硬链接、NUL 和超限文本被拒绝。
- 原阶段一、Alice、Bob 使用三个独立存储域；仅知道其他域的会话 ID 无法取得控制记录。
- 登录凭据不能互用；注销/过期失效，注销关闭对应连接。
- 后端根据已认证身份生成固定插件路径，不接受浏览器指定用户或任意地址。

## 实际 HTTP、WebSocket 与模型调用

运行 `scripts/verify.mjs conversation`，随后在最终 `0.2.0` 镜像重建容器后运行 `resume`。

| 场景 | 观察结果 |
| --- | --- |
| 未登录与跨站请求 | 未登录 401，非本站 Origin 403 |
| 工作区首次与重复初始化 | 两用户工作区 ID 不同，同一用户重复 ensure 返回相同 ID |
| 会话列表与详情 | 列表只包含自己的会话；跨用户详情、历史、消息、恢复、取消均返回 404 |
| 事件越权 | 对方会话订阅在 WebSocket 升级之前返回 404 |
| 并发真实模型 | Alice/Bob 同时执行完整 MCP 身份确认、文件写入和读取 |
| 独立 MCP 凭据 | 从原生工具结果解析 `authenticatedUser`，实际分别是 `alice` 与 `bob` |
| 文件隔离 | 两边 `same-name.txt` 不同；模型分别写入并读回各自 `identity.txt` |
| 模型工具越界 | 实际调用 `workspace_read('../另一用户/identity.txt')`，得到“只允许个人工作区内的相对路径”，不是仅靠模型口头拒绝 |
| 消息幂等 | 相同客户端消息 ID 再次提交返回 duplicate，不重复执行 |
| 事件归属 | 双方都收到真实 assistant.delta，事件 sessionId 始终对应自己的订阅 |
| 并发追问 | 两名用户都进入 WAITING_FOR_INPUT，Bob 回答 Alice 的问题被 404 拒绝 |
| 回答与取消互不干扰 | Alice 自由文本回答继续完成，Bob 取消结束为 CANCELLED |
| 容器重建恢复 | 两边重建后 UNLOADED，可读既有历史，显式 resume 后继续交流 |
| 模型上下文恢复 | 不读取文件，模型从历史回答此前标记；本轮再次调用 whoami 的真实结果仍各自正确 |
| 文件保留 | 重建后读取 identity.txt，与重建前内容完全一致 |

测试会话：Alice `63430c5b-4d6e-415a-8a76-d5ce18d7a980`，Bob `cd542235-4203-4497-ad21-c2433c07f60f`。标记 `phase-two-259b6cf2`。这些是验证产物，不是凭据。

初次运行曾因将会话局部工具名传给只接受全局名称的 `tools.restrict()` 而创建失败；已修正为过滤全局工具、保持局部注册、发布前检查实际工具集，并增加执行期允许清单检查。修复后上述真实验证通过。没有修改 DSH 核心来绕过限制。

## 浏览器与原生数据保留

通过真实浏览器检查登录页及三栏布局。Alice 登录后可查看个人会话、读取真实工具历史与 identity.txt，在页面保存 manual-ui.txt 并发送消息；Bob 登录后只看到自己的会话及文件，未显示 Alice 创建的 manual-ui.txt。容器重建后验证台要求重新登录，原生会话历史仍可加载。工具参数与结果采用可展开记录展示。Bob 页面实际展示原生颜色选择问题，选择“蓝色”并提交后，追问工具返回成功。

部署前后对现有 DSH_HOME 和 workspace 文件做 SHA-256 对比（不扫描依赖、缓存及只读 skills 挂载）：基线 1,945 项，1,944 项不变，0 项删除。唯一改变的是原生工作区注册文件 `storages/workspace.json`，用于登记验证工作区；新增了验证用存储域、会话及个人文件。第一阶段既有主验证会话仍可通过 v1 读取，保留 17 条历史消息。

指纹快照位于不提交的 `.local/dsh-phase-two/`，不包含文件正文。账号密码、Bridge 凭据及 MCP 令牌不进入本记录。脚本状态文件只包含测试会话 ID 和标记。

## 结论边界

已验证：共享实例下受控对话、原生追问、专用身份 MCP、个人文本文件工具和插件接口的用户归属。

未验证或未提供：DataScalpel 登录/RBAC/系统 MCP、真实业务修改、Skills、任意 Shell/代码执行、对恶意容器 root 或原生管理员的隔离、生产账号管理、横向扩容及大规模并发。本次仅两个固定用户，不声称已是生产多租户平台。DSH 原生 `workspace-write` 本身并不构成跨用户读隔离；不得在此验证结论下直接开放任意原生执行工具。

启动、人工复测及限制见[验证台 README](../../integrations/dsh-playground/README.md)。后续继续讨论阶段三，当前未实现 Admin 接入及业务助手页面。
