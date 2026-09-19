# DSH 系统助手权限与工具清单

状态：已实现，适用 DSH `0.1.5-rc.1`、DataScalpel 插件 `0.6.0`。本清单对照已安装的官方发布包与 `datascalpel-admin` 实现核对。

## 权限默认值

镜像、本地 Compose 与 test69 Compose 的 `DSH_PERMISSION_MODE` 均为 `danger-full-access`。原生 DSH 将其映射为 sandbox `danger-full-access` + approval `never`，不修改 DSH 核心。

原生权限服务会把权限写入每个会话。新会话使用当前默认值；持久化设置 `permission.defaultPreset` 若有显式值，则优先于部署默认值；已有会话继续使用自己的权限。本次不批量改写旧会话历史。容器需重新创建以更新环境，test69 需重新部署其 Stack。

权限模式和工具集合分别控制：前者决定原生操作的沙箱与审批行为，后者决定模型能调用哪些功能。系统助手采用 `Sessions.load()` 的作用域限制和执行检查；会话附件归属、系统 MCP 的开放清单与 RBAC 继续生效。助手 UI 暂无原生权限切换控件。

## DataScalpel 专用工具

| 工具 | 能力与当前范围 |
| --- | --- |
| `ask_user_question` | 向用户发起结构化追问并等待回答 |
| `workspace_read` | 列出个人目录或读取 UTF-8 文本；单文件最多 128 KiB |
| `workspace_write` | 创建或覆盖个人工作区内的 UTF-8 文本；单文件最多 128 KiB |
| `attachment_read` | 分段读取当前会话已发送附件的解析内容，包含 Excel 工作表和行列数据 |
| `api_search` | 检索当前用户可访问且已开放的系统 API |
| `api_describe` | 读取候选 API 的最新契约 |
| `api_invoke` | 以当前用户身份调用已开放的 API |

MCP 三个工具在运行时带有会话级 `mcp__system_…__` 前缀。截图通过原生图片消息传给支持图片的模型，不需要 `read_image` 才能识别已上传截图。附件上传、历史展示与下载属于应用能力，不计入模型工具。

## 已接入的 DSH 原生能力

`datascalpel-admin` Preset 显式挂载下列能力，会话以 `both` 模式同时展示原生 Schema 与 `run_code` SDK。工具仍受运行平台、模型、提供方和当前会话状态约束。

| 编号 | 能力 | 工具名 | 接入意义或依赖 |
| --- | --- | --- | --- |
| A | 命令与 Python 执行 | `bash` | 使用持久 Bash，可运行 Python/pandas/openpyxl、命令行和编译工具；同一会话保留 Shell 状态 |
| B | 原生文件读写与编辑 | `read`、`write`、`edit` | 分行读文本、写文件、精确替换，补充当前受控文件工具 |
| C | 文件与内容搜索 | `glob`、`grep` | 按路径模式和文本内容检索文件 |
| D | 工作文件图片读取 | `read_image` | 让模型查看工作目录中的图片；与已支持的截图上传分开 |
| E | Skills | `skill` | 从默认根及 `/workspace/skills` 发现并加载 Skill |
| F | 文件成果交付 | `present` | 把已有文件声明为原生文件成果；当前 DataScalpel UI 仍以普通工具卡展示其结果 |
| G | 网页获取与搜索 | `web_fetch`、`web_search` | 抓取公开网页、检索互联网；搜索依赖可用服务和凭据，默认提供方为 DeepSeek |
| H | 执行进度 | `todo_write` | 维护实施任务清单与完成状态 |
| I | 后台命令管理 | `job_list`、`job_output`、`job_kill` | 查看、读取和停止后台工作，通常与 `bash` 一起接入 |
| J | 子智能体 | `subagent`、`subagent_fork`、`list_subagent_models`、`list_agents`、`send_message`、`interrupt_agent` | 委派、继承上下文、查看和控制 DSH 内建子智能体；子智能体继承当前 Preset 的能力 |
| K | 长目标推进 | `get_goal`、`create_goal`、`update_goal`、`ralph` | 目标状态、自动继续或多轮子智能体迭代，需相关驱动 |
| L | 计划审核 | `exit_plan_mode` | 提交计划并退出原生计划模式 |
| M | 工具编排 | `workflow`、`run_code` | 用工作流或 TypeScript SDK 编排已有工具；会话使用 `both` 呈现模式 |
| N | 替代执行工具 | `str_replace_editor`、持久 `bash`、`pwsh` | 已启用文本替换编辑器和持久 Bash；PowerShell 行仅在 Windows DSH 主机生效，Linux 容器按官方平台门控禁用 |
| O | DSH 插件运行控制 | `cordis_inspect_list`、`cordis_inspect_query`、`cordis_inspect_self`、`cordis_define`、`cordis_run`、`cordis_stop`、`cordis_undefine` | 检查或动态执行 Cordis 插件，影响范围可超过单个业务会话 |

当前发布包的 Codex/Claude Code 子智能体需要额外提供方，未伪装为可用；内建 `spawn` 和 `fork` 子智能体已经启用。上下文压缩通过 `compaction-basic` 接入，但属于运行机制，不是模型工具。

共享实例已开放 `bash`、Cordis 控制和不限制路径的原生文件工具。在完全权限下，它们可以访问容器内其他可见目录；每用户一个工作区只是默认工作目录，不构成进程隔离。Docker 只读挂载仍不可写，宿主机未挂载的路径仍不可见，系统 MCP 调用继续受绑定用户权限约束。
