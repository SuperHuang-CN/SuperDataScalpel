package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateWorkflowTaskDefinitionRequest;

import cn.superhuang.data.scalpel.business.task.web.response.WorkflowDefinitionValidationResponse;

import cn.superhuang.data.scalpel.business.task.web.response.WorkflowTaskDefinitionResponse;

import cn.superhuang.data.scalpel.business.task.service.WorkflowTaskDefinitionService;

import cn.superhuang.data.scalpel.business.task.service.DataTaskService;
import cn.superhuang.data.scalpel.business.task.service.CanvasTaskDefinitionService;
import cn.superhuang.data.scalpel.business.task.service.TaskStreamingService;
import cn.superhuang.data.scalpel.business.task.service.TaskModelRelationQueryService;
import cn.superhuang.data.scalpel.business.task.service.ModelQualityTaskDefinitionService;
import cn.superhuang.data.scalpel.business.task.service.SparkJarTaskDefinitionService;
import cn.superhuang.data.scalpel.business.task.service.SparkJarDevelopmentKitService;
import cn.superhuang.data.scalpel.business.task.service.SparkJarTrialRunService;
import cn.superhuang.data.scalpel.business.task.service.TaskRunService;
import cn.superhuang.data.scalpel.business.lineage.service.TaskLineageQueryService;
import cn.superhuang.data.scalpel.business.lineage.web.response.TaskLineageGraphResponse;
import cn.superhuang.data.scalpel.business.lineage.web.response.TaskFieldLineageGraphResponse;
import cn.superhuang.data.scalpel.business.task.web.request.CreateDataTaskRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateCanvasTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateDataTaskRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateLocalSqlTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateTaskStreamingConfigurationRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateModelQualityTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateSparkJarTaskDefinitionRequest;
import cn.superhuang.data.scalpel.business.task.web.request.StartStreamingTaskRequest;
import cn.superhuang.data.scalpel.business.task.web.request.QueryTaskFieldLineageRequest;
import cn.superhuang.data.scalpel.business.task.web.request.CreateSparkJarDevelopmentKitRequest;
import cn.superhuang.data.scalpel.business.task.web.request.SaveSparkJarOnlineSourceRequest;
import cn.superhuang.data.scalpel.business.task.web.request.CanvasTrialRunRequest;
import cn.superhuang.data.scalpel.business.task.web.response.DataTaskResponse;
import cn.superhuang.data.scalpel.business.task.web.response.CanvasTaskDefinitionResponse;
import cn.superhuang.data.scalpel.business.task.web.response.LocalSqlDefinitionValidationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.LocalSqlTaskDefinitionResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingConfigurationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskStreamingStatusResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskModelRelationsResponse;
import cn.superhuang.data.scalpel.business.task.web.response.ModelQualityTaskDefinitionResponse;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarTaskDefinitionResponse;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarDevelopmentKitResponse;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarOnlineCompilationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarOnlineSourceResponse;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarTrialRunResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "任务管理")
public class DataTaskResource {

    private final WorkflowTaskDefinitionService workflowDefinitions;
    private final DataTaskService service;
    private final CanvasTaskDefinitionService canvasDefinitionService;
    private final TaskStreamingService streamingService;
    private final TaskModelRelationQueryService taskModelRelationQueryService;
    private final TaskLineageQueryService taskLineageQueryService;
    private final ModelQualityTaskDefinitionService modelQualityTaskDefinitionService;
    private final SparkJarTaskDefinitionService sparkJarTaskDefinitionService;
    private final SparkJarDevelopmentKitService sparkJarDevelopmentKitService;
    private final SparkJarTrialRunService sparkJarTrialRunService;
    private final TaskRunService taskRunService;

    public DataTaskResource(
            WorkflowTaskDefinitionService workflowDefinitions,
            DataTaskService service,
            CanvasTaskDefinitionService canvasDefinitionService,
            TaskStreamingService streamingService,
            TaskModelRelationQueryService taskModelRelationQueryService,
            TaskLineageQueryService taskLineageQueryService,
            ModelQualityTaskDefinitionService modelQualityTaskDefinitionService,
            SparkJarTaskDefinitionService sparkJarTaskDefinitionService,
            SparkJarDevelopmentKitService sparkJarDevelopmentKitService,
            SparkJarTrialRunService sparkJarTrialRunService,
            TaskRunService taskRunService
    ) {
        this.workflowDefinitions = workflowDefinitions;
        this.service = service;
        this.canvasDefinitionService = canvasDefinitionService;
        this.streamingService = streamingService;
        this.taskModelRelationQueryService = taskModelRelationQueryService;
        this.taskLineageQueryService = taskLineageQueryService;
        this.modelQualityTaskDefinitionService = modelQualityTaskDefinitionService;
        this.sparkJarTaskDefinitionService = sparkJarTaskDefinitionService;
        this.sparkJarDevelopmentKitService = sparkJarDevelopmentKitService;
        this.sparkJarTrialRunService = sparkJarTrialRunService;
        this.taskRunService = taskRunService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务")
    @GetMapping
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务", description = "分页查询任务基本信息、类型、发布状态和定义摘要，不读取完整定义或运行历史。")
    public PageResponse<DataTaskResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务摘要")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务摘要", description = "读取任务基本信息、类型、生命周期状态和定义摘要；完整定义由对应类型的定义接口返回。")
    public DataTaskResponse get(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询本地 SQL 任务定义")
    @GetMapping("/{id}/definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询本地 SQL 任务定义", description = "读取 LOCAL_SQL 任务当前保存的输入模型、查询 SQL、输出模型、写入模式和超时；未配置时返回带默认 writeMode=APPEND、timeoutSeconds=300 的空定义。不连接数据源。")
    public LocalSqlTaskDefinitionResponse getDefinition(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return service.getDefinition(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Spark Canvas 任务定义")
    @GetMapping("/{id}/canvas-definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark Canvas 任务定义", description = "读取 Spark Canvas 任务当前保存的稳定画布定义、节点和连线，不编译或运行。")
    public CanvasTaskDefinitionResponse getCanvasDefinition(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return canvasDefinitionService.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "使用真实数据试运行 Canvas 到指定节点",
            prerequisites = "仅支持 DRAFT 或 DISABLED 的 SPARK_CANVAS；必须至少保存过一次定义、baseDefinitionVersion 与当前版本一致、任务没有活动运行、计算引擎可用，目标必须是草稿中的 Input 或 Processor 节点。",
            relatedOperations = {"GET /api/v1/tasks/{id}/canvas-definition", "GET /api/v1/task-runs/{runId}", "GET /api/v1/task-runs/{runId}/canvas-trial-preview"})
    @PostMapping("/{id}/canvas-definition/actions/trial-run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "使用真实数据试运行 Canvas 到指定节点", description = "仅支持 DRAFT 或 DISABLED 的 SPARK_CANVAS。要求已保存定义版本与 baseDefinitionVersion 一致、任务没有活动运行且计算引擎可用；服务端升级并校验请求草稿，只执行目标 Input/Processor 节点及其上游闭包。草稿不会保存，Output 不会执行，真实绑定输入可能被读取。202 仅表示运行已排队，应查询运行状态，并在终态查询 Canvas 预览。")
    public TaskRunResponse trialRunCanvas(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody CanvasTrialRunRequest request
    ) {
        return taskRunService.submitCanvasTrial(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Spark 模型质检任务定义")
    @GetMapping("/{id}/model-quality-definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark 模型质检任务定义", description = "读取 Spark 模型质检任务当前保存的目标模型、规则和样本配置，不执行质检。")
    public ModelQualityTaskDefinitionResponse getModelQualityDefinition(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return modelQualityTaskDefinitionService.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Spark JAR 任务定义")
    @GetMapping("/{id}/spark-jar-definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark JAR 任务定义", description = "读取 Spark JAR 任务的入口类、制品摘要、参数和资源绑定，不下载 JAR 正文。")
    public SparkJarTaskDefinitionResponse getSparkJarDefinition(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return sparkJarTaskDefinitionService.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Spark JAR 在线 Java 源码",
            prerequisites = "任务类型必须是 SPARK_JAR 或 SPARK_STREAMING_JAR。",
            relatedOperations = {"GET /api/v1/tasks/{id}/spark-jar-definition"})
    @GetMapping("/{id}/spark-jar-online-source")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark JAR 在线 Java 源码", description = "读取批处理或流式 Spark JAR 在线开发区当前保存的 Java 源码、源码摘要和最近编译摘要；尚未创建 JAR 定义时返回对应 Job Mode 的默认源码，definitionVersion=0，不写入数据库。")
    public SparkJarOnlineSourceResponse getSparkJarOnlineSource(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return sparkJarTaskDefinitionService.getOnlineSource(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "保存 Spark JAR 在线 Java 源码草稿",
            prerequisites = "任务类型必须是 SPARK_JAR 或 SPARK_STREAMING_JAR，状态必须是 DRAFT 或 DISABLED；源码 UTF-8 大小不得超过 256 KiB。",
            relatedOperations = {"GET /api/v1/tasks/{id}/spark-jar-online-source", "POST /api/v1/tasks/{id}/spark-jar-online-source/actions/compile", "POST /api/v1/tasks/{id}/spark-jar-online-source/actions/trial-run"})
    @PostMapping("/{id}/spark-jar-online-source/actions/save")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "保存 Spark JAR 在线 Java 源码草稿", description = "仅允许 DRAFT 或 DISABLED 的批处理或流式 Spark JAR。规范化并整体保存在线 Java 源码；首次保存可创建 JAR 定义。不会编译、不会替换当前生产 JAR、不会发布或运行任务。")
    public SparkJarOnlineSourceResponse saveSparkJarOnlineSource(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id, @Valid @RequestBody SaveSparkJarOnlineSourceRequest request) {
        return sparkJarTaskDefinitionService.saveOnlineSource(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "编译并应用 Spark JAR 在线 Java 源码",
            prerequisites = "任务类型必须是 SPARK_JAR 或 SPARK_STREAMING_JAR，状态必须是 DRAFT 或 DISABLED；请求源码会先保存，编译成功后才替换当前生产 JAR。",
            relatedOperations = {"GET /api/v1/tasks/{id}/spark-jar-online-source", "GET /api/v1/tasks/{id}/spark-jar-definition"})
    @PostMapping("/{id}/spark-jar-online-source/actions/compile")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "编译并应用 Spark JAR 在线 Java 源码", description = "仅允许 DRAFT 或 DISABLED 的批处理或流式 Spark JAR。先保存请求源码，再通过 Task Engine 编译：FAILED 时源码草稿仍已保存且当前生产 JAR不变；SUCCEEDED 时校验摘要、入口类、SDK 版本和 Job Mode 后替换当前 JAR并记录已编译源码摘要。不会发布或运行任务。")
    public SparkJarOnlineCompilationResponse compileSparkJarOnlineSource(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id, @Valid @RequestBody SaveSparkJarOnlineSourceRequest request) {
        return sparkJarTaskDefinitionService.compileOnlineSource(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "使用真实数据试运行 Spark JAR 在线 Java 源码",
            prerequisites = "任务类型必须是 SPARK_JAR 或 SPARK_STREAMING_JAR，状态必须是 DRAFT 或 DISABLED；需要已保存的任务定义、可用计算引擎和资源绑定，且不能有活动运行；流式 JAR 还不能有活动部署。",
            relatedOperations = {"GET /api/v1/tasks/{id}/spark-jar-online-source", "GET /api/v1/task-runs/{runId}", "GET /api/v1/task-runs/{runId}/trial-preview", "POST /api/v1/task-runs/{runId}/actions/stop"})
    @PostMapping("/{id}/spark-jar-online-source/actions/trial-run")
    @PreAuthorize("hasAuthority('task.update') and hasAuthority('task.execute')")
    @Operation(summary = "使用真实数据试运行 Spark JAR 在线 Java 源码", description = "仅允许 DRAFT 或 DISABLED 的批处理或流式 Spark JAR。请求源码会先保存并编译：编译失败返回 HTTP 200、status=COMPILE_FAILED 且不创建运行；成功后用临时 JAR和真实绑定资源创建隔离试运行，返回 HTTP 202、status=QUEUED。临时 JAR不替换当前生产 JAR。批任务采用定义 timeoutSeconds；流式任务固定最多 30 分钟并使用隔离的 FRESH Checkpoint。")
    public ResponseEntity<SparkJarTrialRunResponse> trialRunSparkJarOnlineSource(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id, @Valid @RequestBody SaveSparkJarOnlineSourceRequest request) {
        SparkJarTrialRunResponse response = sparkJarTrialRunService.run(id, request);
        return ResponseEntity.status(response.status() == SparkJarTrialRunResponse.Status.QUEUED
                ? HttpStatus.ACCEPTED : HttpStatus.OK).body(response);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载 Spark JAR Maven 初始化工程")
    @GetMapping("/{id}/spark-jar-template")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "下载 Spark JAR Maven 初始化工程", description = "下载按当前任务生成的 Maven 初始化工程 ZIP；文件下载不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> downloadSparkJarTemplate(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"datascalpel-spark-job-template.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(sparkJarTaskDefinitionService.template(id));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询当前 Spark JAR 本地开发包",
            prerequisites = "任务类型必须是 SPARK_JAR 或 SPARK_STREAMING_JAR，且必须已经保存任务定义。",
            relatedOperations = {"GET /api/v1/tasks/{id}/spark-jar-definition", "POST /api/v1/tasks/{id}/spark-jar-development-kit/actions/generate"})
    @GetMapping("/{id}/spark-jar-development-kit")
    @PreAuthorize("hasAuthority('task.update') and hasAuthority('model.view') and hasAuthority('datasource.metadata')")
    @Operation(summary = "查询当前 Spark JAR 本地开发包", description = "读取批处理或流式 Spark JAR 任务当前保存的采样配置、最近一次生成请求，以及最近成功并仍作为当前指针的 ZIP 元数据；不会读取 ZIP 正文。尚未保存 Spark JAR 定义时返回 409。")
    public SparkJarDevelopmentKitResponse getSparkJarDevelopmentKit(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return sparkJarDevelopmentKitService.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "生成或重新生成 Spark JAR 本地开发包",
            prerequisites = "任务类型必须是 SPARK_JAR 或 SPARK_STREAMING_JAR，已保存定义且 definitionVersion 与当前值一致；引用的模型与 JDBC 数据源绑定必须存在并可读，同时不能已有 QUEUED 或 RUNNING 的生成请求。",
            relatedOperations = {"GET /api/v1/tasks/{id}/spark-jar-development-kit", "GET /api/v1/tasks/{id}/spark-jar-development-kit/artifact"})
    @PostMapping("/{id}/spark-jar-development-kit/actions/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.update') and hasAuthority('model.view') and hasAuthority('datasource.metadata')")
    @Operation(summary = "生成或重新生成 Spark JAR 本地开发包", description = "校验 definitionVersion 和可读资源绑定，保存规范化采样配置并异步生成开发包；可包含未自动脱敏的真实模型或 JDBC 表样本。已有 QUEUED/RUNNING 生成请求时返回 409；202 表示已排队，应查询 generation 状态。新包成功前旧的当前制品仍可下载。")
    public SparkJarDevelopmentKitResponse generateSparkJarDevelopmentKit(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id, @Valid @RequestBody CreateSparkJarDevelopmentKitRequest request) {
        return sparkJarDevelopmentKitService.generate(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载当前 Spark JAR 本地开发包",
            prerequisites = "必须已有最近成功且对象仍存在的开发包制品；下载是文件响应，系统 MCP 第一版不能调用。",
            relatedOperations = {"GET /api/v1/tasks/{id}/spark-jar-development-kit"})
    @GetMapping("/{id}/spark-jar-development-kit/artifact")
    @PreAuthorize("hasAuthority('task.update') and hasAuthority('model.view') and hasAuthority('datasource.metadata')")
    @Operation(summary = "下载当前 Spark JAR 本地开发包", description = "流式下载任务指向的最近成功 ZIP；它可能与刚修改但尚未成功重新生成的采样配置不匹配，应先检查 artifact.matchesSavedConfiguration。对象已丢失时返回 410；文件下载不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<StreamingResponseBody> downloadSparkJarDevelopmentKit(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        SparkJarDevelopmentKitService.ArtifactDownload artifact = sparkJarDevelopmentKitService.artifact(id);
        StreamingResponseBody body = output -> {
            try (var content = artifact.content(); var input = content.inputStream()) {
                input.transferTo(output);
            }
        };
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"")
                .contentLength(artifact.size())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询当前保存任务定义引用的模型",
            prerequisites = "任务必须存在；没有对应类型定义时返回 configured=false 和空引用列表。",
            relatedOperations = {"GET /api/v1/tasks/{id}"})
    @GetMapping("/{id}/model-relations")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询当前保存任务定义引用的模型", description = "读取当前保存任务定义直接引用的输入、输出模型及角色；这是定义投影，不代表某次历史运行。")
    public TaskModelRelationsResponse getModelRelations(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return taskModelRelationQueryService.getTaskModelRelations(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务当前表级血缘")
    @GetMapping("/{id}/lineage/table")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务当前表级血缘", description = "读取任务当前未退役血缘快照中的一条输出流及其表级输入输出图；flows 同时列出所有可选输出流。任务停用后修改但尚未重新启用时，当前快照仍可能对应上次发布版本；不返回历史快照。")
    public TaskLineageGraphResponse getTableLineage(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Parameter(description = "可选输出流稳定键；为空或全空白时选择按 flowKey 排序的第一条流。响应 flows 仍列出所有候选流，但 graph 只对应 selectedFlowKey；未知值返回 404。") @RequestParam(required = false) String flowKey
    ) {
        return taskLineageQueryService.table(id, flowKey);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务当前字段级血缘")
    @GetMapping("/{id}/lineage/fields")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务当前字段级血缘", description = "读取任务当前未退役血缘快照中一条输出流的字段级转换图。未指定输出字段时默认聚焦该流前 20 个输出字段，而非全部；大图最多 200 个节点、600 条关系并显式标记截断。")
    public TaskLineageGraphResponse getFieldLineage(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Parameter(description = "可选输出流稳定键；为空或全空白时选择按 flowKey 排序的第一条流，未知值返回 404。") @RequestParam(required = false) String flowKey,
            @Parameter(description = "可选输出字段稳定键；指定时只聚焦该字段，未知值返回 404；为空时默认选择所选流按 sortOrder 排列的前 20 个字段。") @RequestParam(required = false) String outputFieldKey
    ) {
        return taskLineageQueryService.fields(id, flowKey, outputFieldKey);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "批量查询任务当前输出链路的字段级血缘")
    @PostMapping("/{id}/lineage/actions/query-fields")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "批量查询任务当前输出链路的字段级血缘", description = "从当前未退役血缘快照选择一条输出流，批量聚焦最多 50 个输出字段。请求字段列表省略时默认前 20 个，空数组返回空字段图；任一字段不属于所选流时整次返回 404。")
    public TaskFieldLineageGraphResponse queryFieldLineage(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody QueryTaskFieldLineageRequest request
    ) {
        return taskLineageQueryService.fieldLines(id, request.flowKey(), request.outputFieldKeys());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询实时任务配置")
    @GetMapping("/{id}/streaming-configuration")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询实时 Canvas 微批配置", description = "读取 SPARK_STREAMING_CANVAS 当前唯一无界输入节点的微批触发间隔；未保存 Canvas 定义时返回默认间隔。不适用于 SPARK_STREAMING_JAR，也不查询部署状态。")
    public TaskStreamingConfigurationResponse getStreamingConfiguration(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return streamingService.getConfiguration(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询实时任务运行状态")
    @GetMapping("/{id}/streaming-status")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询实时任务运行状态", description = "读取 SPARK_STREAMING_CANVAS 或 SPARK_STREAMING_JAR 按定义版本和 Checkpoint 代次排序的最新正式部署、当前运行、查询进度与 TMQ 清理计数；不返回隔离的试运行部署。")
    public TaskStreamingStatusResponse getStreamingStatus(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return streamingService.status(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建任务")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task.create')")
    @Operation(summary = "创建任务", description = "创建指定类型的任务草稿和基础信息；不会自动生成完整定义、发布或运行。")
    public DataTaskResponse create(@Valid @RequestBody CreateDataTaskRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改任务基本信息")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "修改任务基本信息", description = "修改任务名称、目录、说明等基本信息；任务类型和已保存定义保持不变。")
    public DataTaskResponse update(@Parameter(description = "任务 UUID。") @PathVariable UUID id, @Valid @RequestBody UpdateDataTaskRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "整体保存本地 SQL 任务定义")
    @PostMapping("/{id}/actions/update-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "整体保存本地 SQL 任务定义", description = "整体替换 DRAFT 或 DISABLED 本地 SQL 任务定义，并校验 SQL 语法、输入输出模型、同源关系和写入模式；不会连接数据源执行 SQL，也不会自动发布。")
    public LocalSqlTaskDefinitionResponse updateDefinition(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateLocalSqlTaskDefinitionRequest request
    ) {
        return service.updateDefinition(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "整体保存 Spark Canvas 任务定义")
    @PostMapping("/{id}/actions/update-canvas-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "整体保存 Spark Canvas 任务定义", description = "整体替换 DRAFT 或 DISABLED Spark Canvas 定义，升级兼容协议并校验节点、连线和配置；流式任务存在 STARTING、RUNNING 或 STOPPING 部署时拒绝修改。不会编译、发布或运行。")
    public CanvasTaskDefinitionResponse updateCanvasDefinition(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateCanvasTaskDefinitionRequest request
    ) {
        return canvasDefinitionService.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "保存 Spark 模型质检任务定义")
    @PostMapping("/{id}/actions/update-model-quality-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "保存 Spark 模型质检任务定义", description = "整体保存 DRAFT 或 DISABLED 模型质检任务的目标模型和失败样本上限；规则仍由目标模型维护，本接口不复制或修改规则，也不会扫描数据。")
    public ModelQualityTaskDefinitionResponse updateModelQualityDefinition(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateModelQualityTaskDefinitionRequest request
    ) {
        return modelQualityTaskDefinitionService.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "保存 Spark JAR 任务定义")
    @PostMapping("/{id}/actions/update-spark-jar-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "保存 Spark JAR 任务定义", description = "整体替换 DRAFT 或 DISABLED Spark JAR 任务的普通参数、受控 Spark 配置、资源绑定和运行资源；保留当前 JAR 与在线源码，不发布也不运行。")
    public SparkJarTaskDefinitionResponse updateSparkJarDefinition(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateSparkJarTaskDefinitionRequest request
    ) {
        return sparkJarTaskDefinitionService.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "上传或覆盖 Spark JAR")
    @PostMapping(value = "/{id}/actions/upload-spark-jar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "上传或覆盖 Spark JAR", description = "上传并覆盖任务当前 Spark JAR，校验大小、摘要、SDK 兼容性和入口类；文件上传不属于系统 MCP 第一版支持范围。")
    public SparkJarTaskDefinitionResponse uploadSparkJar(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Parameter(description = "待上传的 Spark JAR 文件；服务端校验文件大小、摘要、SDK 版本和入口类。") @RequestPart("file") MultipartFile file
    ) {
        return sparkJarTaskDefinitionService.upload(id, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改实时 Canvas 微批间隔")
    @PostMapping("/{id}/actions/update-streaming-configuration")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "修改实时 Canvas 微批间隔", description = "修改 SPARK_STREAMING_CANVAS 当前定义中唯一无界输入节点的微批触发间隔，并按内容变化更新 Canvas 定义版本；有活动部署时拒绝修改。不适用于 SPARK_STREAMING_JAR，也不会启动任务。")
    public TaskStreamingConfigurationResponse updateStreamingConfiguration(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskStreamingConfigurationRequest request
    ) {
        return streamingService.updateConfiguration(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "启动或恢复实时任务")
    @PostMapping("/{id}/actions/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "启动或恢复实时任务", description = "异步启动已发布的 SPARK_STREAMING_CANVAS 或 SPARK_STREAMING_JAR。JAR 任务必须在请求体选择 CONTINUE 或 FRESH；Canvas 使用定义版本对应的部署和 Checkpoint，省略请求体即可。已有活动正式部署时幂等返回其状态；202 表示已受理，应继续查询实时状态。")
    public TaskStreamingStatusResponse startStreaming(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id,
            @Valid @RequestBody(required = false) StartStreamingTaskRequest request
    ) {
        return streamingService.start(id, request == null ? null : request.checkpointMode());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "正常停止实时任务")
    @PostMapping("/{id}/actions/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "正常停止实时任务", description = "请求停止最新正式实时部署并由执行器执行最多 60 秒的正常停止；不会操作隔离试运行。已处于 STOPPING 或 STOPPED 时幂等返回当前状态；202 表示已受理，不表示后端已经停止。")
    public TaskStreamingStatusResponse stopStreaming(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return streamingService.stop(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "校验本地 SQL 任务定义")
    @PostMapping("/{id}/actions/validate-definition")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "校验本地 SQL 任务定义", description = "对当前已保存 LOCAL_SQL 定义执行发布前预检：校验模型与物理表状态、SQL 声明表、输出列类型、主键、方言写入能力和字段血缘。会连接只读 JDBC，会在驱动无法直接提供元数据时执行最多返回 1 行的查询以获取 ResultSetMetaData；不写入、不保存定义或发布。")
    public LocalSqlDefinitionValidationResponse validateDefinition(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return service.validateDefinition(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "发布任务")
    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "发布任务", description = "校验当前任务类型对应的完整定义和资源绑定后发布可运行版本；不会立即创建任务运行。")
    public DataTaskResponse publish(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return service.publish(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用任务")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "停用任务", description = "停用已发布任务，阻止新的手工和计划运行；不会自动取消已经提交或运行中的实例。")
    public DataTaskResponse disable(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return service.disable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "重新启用任务")
    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "重新启用任务", description = "重新校验已停用任务的当前定义和资源后恢复可运行状态；不会立即运行任务。")
    public DataTaskResponse enable(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return service.enable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除草稿或已停用任务")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task.delete')")
    @Operation(summary = "删除草稿或已停用任务", description = "永久删除 DRAFT 或 DISABLED 且从未产生运行记录的任务，以及其当前定义、计划、引用索引和当前血缘；PUBLISHED 任务必须先停用。不会删除模型、数据源或外部业务数据，JAR 制品在事务提交后清理。")
    public void delete(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        service.delete(id);
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询工作流定义")
    @GetMapping("/{id}/workflow-definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询工作流定义", description = "读取工作流当前保存的节点、子任务和依赖定义，不查询某次运行状态。")
    public WorkflowTaskDefinitionResponse workflowDefinition(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return workflowDefinitions.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "整体保存工作流定义")
    @PostMapping("/{id}/actions/update-workflow-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "整体保存工作流定义", description = "整体替换 DRAFT 或 DISABLED 工作流的节点、依赖和布局，仅校验当前协议版本；子任务可用性、节点约束和无环结构由校验或发布接口检查。本接口不会发布或运行。")
    public WorkflowTaskDefinitionResponse updateWorkflowDefinition(
            @Parameter(description = "任务 UUID。") @PathVariable UUID id, @Valid @RequestBody UpdateWorkflowTaskDefinitionRequest request) {
        return workflowDefinitions.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "校验工作流定义")
    @PostMapping("/{id}/actions/validate-workflow-definition")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "校验工作流定义", description = "校验当前保存工作流的节点引用、依赖拓扑和可运行条件，返回问题清单但不修改任务。")
    public WorkflowDefinitionValidationResponse validateWorkflowDefinition(@Parameter(description = "任务 UUID。") @PathVariable UUID id) {
        return workflowDefinitions.validate(id);
    }

}
