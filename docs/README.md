# 开发文档入口

按本次修改的业务与能力选读。先读 [根开发约定](../AGENTS.md)和适用的模块 AGENTS，再读下表对应专题；不要求每次任务加载全部设计文档。

## 规范、设计与计划

- **AGENTS.md**：高频工程约束、模块边界和必读入口。
- **development/**：现行专题开发规范。页面细节、运行语义等在此集中维护，AGENTS 只保留关键摘要。
- **design/**：功能设计、协议及实施计划。已实施计划保留决策背景；其旧版本、迁移顺序、回退假设和“本次验收”不作为新任务要求。待实施设计也不表示功能已经支持。
- **operations/**：特定升级、数据处理和运维步骤，按文档的适用版本与前置条件使用。

维护文档时注明状态与适用范围；计划完成后链接到现行规范。只在主题所属文档维护完整规则，其他位置保留摘要与链接。发现实现和设计冲突时先核对用户已确认的决策，不能仅凭代码或旧计划擅自改变业务语义。测试政策集中维护在 [根约定](../AGENTS.md#测试与验证暂时禁用)。

## 按任务阅读

| 修改内容 | 入口 |
| --- | --- |
| 本地启动、功能自测或联调 | [启动约定](../AGENTS.md#本地开发与调试)、[脚本用法](../README.md#本地开发与调试) |
| 前端结构、API、状态、Canvas Inspector | [前端 AGENTS](../data-scalpel-ui/AGENTS.md) |
| 页面、表格、筛选、目录、表单或视觉 | [页面与交互规范](development/frontend-ui.md)，按文内表格定位章节 |
| 任务中心菜单分组、列表与详情导航 | [任务中心分组](development/frontend-ui.md#task-center-views) |
| Canvas 节点、编译、Runner、批流或 SDK | [Task Engine AGENTS](../data-scalpel-task-engine/AGENTS.md)、[编译与执行规范](development/task-engine.md) |
| 任务工作流、依赖、父子运行和取消 | [TaskWorkflow V1](design/task-workflow-v1.md) |
| 全局运行工作台、告警与通知 | [运行工作台与告警 V1](design/global-runtime-workbench-and-alerts.md)（V1 实现、规则、通知契约与部署配置） |
| Canvas JSON、节点配置、设计期 API | [定义与节点语义](design/canvas-task-definition.md)、[编译服务](design/task-engine-daemon-and-compilation.md) |
| Manifest、结果、Kafka 消息、Dispatcher | [Canvas 执行](design/canvas-task-execution.md)、[消息可靠性](design/task-execution-platform/02-kafka-contracts-and-admin-reliability.md)、[Runner 与制品](design/task-execution-platform/05-task-runner-kafka-and-artifacts.md) |
| 用户 JAR、TestKit、在线开发及试运行 | [SDK 设计](design/spark-jar-task-sdk-v1.md)、[SDK 运行约束](development/task-engine.md#spark-jar) |
| HTTP 错误、认证、权限 | [错误处理](design/backend-api-response-and-error-handling.md)、[系统访问管理](design/system-access-management.md) |
| JDBC、平台类型、模型及物理表 | [数据源](design/data-source-management.md)、[类型系统](design/model-data-type-system.md)、[模型](design/model-management.md)、[物理表演进](design/model-physical-table-evolution.md) |
| 全景成品、地图浏览和全景资产 | [全景影像管理 V1](development/panorama-management-v1.md) |
| 文件数据集及空间文件 | [文件数据集](design/file-dataset-management.md)、[空间文件解析](design/geospatial-file-dataset-parsing.md)及对应格式专题 |
| 数据服务、引擎或网关发布 | [数据服务](design/data-service-publishing.md)、[网关发布](design/data-service-gateway-publishing.md)、[GeoServer](design/geoserver-spatial-service-publishing-v1.md) |
| 独立 Super API Gateway | [网关 AGENTS](../super-api-gateway/AGENTS.md)、[Provider 集成](design/super-api-gateway-provider-integration.md) |
| 数据标准、填报、资产或血缘 | [码表](design/standard-dictionary-management.md)、[填报](design/data-entry-v1.md)、[资产](design/asset-publication-and-synchronization.md)、[血缘](design/lineage-integration-contract.md) |
| 业务指标、口径说明、结果模型绑定、任务反查或 Excel 整理 | [指标管理 V1](design/metric-management-v1.md)、[关联示例](design/metric-management-v1.md#12-关联与口径维护示例)、[Excel 导入导出](design/metric-management-v1.md#13-excel-导入导出与线下整理)（已实现口径、结果绑定、任务关联及 Excel 整理；指标数据结果查询后续开发） |
| 系统 MCP、API 开放和智能体访问 | [系统 MCP](design/system-mcp.md)（独立入口、目录、令牌、调用边界与运维） |
| 第三方 Agent 使用系统、源数据分析与建模 | [DataScalpel Skill](../skills/datascalpel/SKILL.md)（统一入口；首版提供分析、方案确认与模型草稿创建流程） |
| AI 助手或 MCP 平台 | [AI 助手](design/ai-assistant.md)、[MCP 平台](design/mcp-platform.md) |

## 协议版本定位

协议升级前核对以下读写端，避免把设计文档中旧示例的数字当作当前版本：

| 协议 | 代码入口 |
| --- | --- |
| Canvas | [CanvasDefinition](../data-scalpel-contracts/src/main/java/cn/superhuang/data/scalpel/contract/task/CanvasDefinition.java)的大/小版本与 [CanvasNodeType](../data-scalpel-contracts/src/main/java/cn/superhuang/data/scalpel/contract/task/CanvasNodeType.java)的引入版本 |
| Manifest | Admin [CanvasTaskRunManifest](../data-scalpel-business/src/main/java/cn/superhuang/data/scalpel/business/task/service/CanvasTaskRunManifest.java)、Runner [TaskExecutionManifest](../data-scalpel-task-engine/src/main/java/cn/superhuang/datascalpel/taskengine/contract/TaskExecutionManifest.java)和 [ManifestVersionSupport](../data-scalpel-task-engine/src/main/java/cn/superhuang/datascalpel/taskengine/runner/ManifestVersionSupport.java) |
| Result | Runner [TaskExecutionResult](../data-scalpel-task-engine/src/main/java/cn/superhuang/datascalpel/taskengine/contract/TaskExecutionResult.java)、Dispatcher [DispatcherTaskResult](../data-scalpel-task-dispatcher/src/main/java/cn/superhuang/data/scalpel/dispatcher/artifact/DispatcherTaskResult.java)及其校验逻辑 |

技术栈和依赖版本由根/模块 POM 与前端 package.json 声明；构建、本地启动及稳定 Search DSL 见 [项目 README](../README.md)。
