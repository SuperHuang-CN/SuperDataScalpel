package cn.superhuang.data.scalpel.business.task.web.resource;

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

    @GetMapping
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务")
    public PageResponse<DataTaskResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务摘要")
    public DataTaskResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询本地 SQL 任务定义")
    public LocalSqlTaskDefinitionResponse getDefinition(@PathVariable UUID id) {
        return service.getDefinition(id);
    }

    @GetMapping("/{id}/canvas-definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark Canvas 任务定义")
    public CanvasTaskDefinitionResponse getCanvasDefinition(@PathVariable UUID id) {
        return canvasDefinitionService.get(id);
    }

    @PostMapping("/{id}/canvas-definition/actions/trial-run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "使用真实数据试运行 Canvas 到指定节点")
    public TaskRunResponse trialRunCanvas(
            @PathVariable UUID id,
            @Valid @RequestBody CanvasTrialRunRequest request
    ) {
        return taskRunService.submitCanvasTrial(id, request);
    }

    @GetMapping("/{id}/model-quality-definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark 模型质检任务定义")
    public ModelQualityTaskDefinitionResponse getModelQualityDefinition(@PathVariable UUID id) {
        return modelQualityTaskDefinitionService.get(id);
    }

    @GetMapping("/{id}/spark-jar-definition")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark JAR 任务定义")
    public SparkJarTaskDefinitionResponse getSparkJarDefinition(@PathVariable UUID id) {
        return sparkJarTaskDefinitionService.get(id);
    }

    @GetMapping("/{id}/spark-jar-online-source")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark JAR 在线 Java 源码")
    public SparkJarOnlineSourceResponse getSparkJarOnlineSource(@PathVariable UUID id) {
        return sparkJarTaskDefinitionService.getOnlineSource(id);
    }

    @PostMapping("/{id}/spark-jar-online-source/actions/save")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "保存 Spark JAR 在线 Java 源码草稿")
    public SparkJarOnlineSourceResponse saveSparkJarOnlineSource(
            @PathVariable UUID id, @Valid @RequestBody SaveSparkJarOnlineSourceRequest request) {
        return sparkJarTaskDefinitionService.saveOnlineSource(id, request);
    }

    @PostMapping("/{id}/spark-jar-online-source/actions/compile")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "编译并应用 Spark JAR 在线 Java 源码")
    public SparkJarOnlineCompilationResponse compileSparkJarOnlineSource(
            @PathVariable UUID id, @Valid @RequestBody SaveSparkJarOnlineSourceRequest request) {
        return sparkJarTaskDefinitionService.compileOnlineSource(id, request);
    }

    @PostMapping("/{id}/spark-jar-online-source/actions/trial-run")
    @PreAuthorize("hasAuthority('task.update') and hasAuthority('task.execute')")
    @Operation(summary = "使用真实数据试运行 Spark JAR 在线 Java 源码")
    public ResponseEntity<SparkJarTrialRunResponse> trialRunSparkJarOnlineSource(
            @PathVariable UUID id, @Valid @RequestBody SaveSparkJarOnlineSourceRequest request) {
        SparkJarTrialRunResponse response = sparkJarTrialRunService.run(id, request);
        return ResponseEntity.status(response.status() == SparkJarTrialRunResponse.Status.QUEUED
                ? HttpStatus.ACCEPTED : HttpStatus.OK).body(response);
    }

    @GetMapping("/{id}/spark-jar-template")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "下载 Spark JAR Maven 初始化工程")
    public ResponseEntity<byte[]> downloadSparkJarTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"datascalpel-spark-job-template.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(sparkJarTaskDefinitionService.template(id));
    }

    @GetMapping("/{id}/spark-jar-development-kit")
    @PreAuthorize("hasAuthority('task.update') and hasAuthority('model.view') and hasAuthority('datasource.metadata')")
    @Operation(summary = "查询当前 Spark JAR 本地开发包")
    public SparkJarDevelopmentKitResponse getSparkJarDevelopmentKit(@PathVariable UUID id) {
        return sparkJarDevelopmentKitService.get(id);
    }

    @PostMapping("/{id}/spark-jar-development-kit/actions/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.update') and hasAuthority('model.view') and hasAuthority('datasource.metadata')")
    @Operation(summary = "生成或重新生成 Spark JAR 本地开发包")
    public SparkJarDevelopmentKitResponse generateSparkJarDevelopmentKit(
            @PathVariable UUID id, @Valid @RequestBody CreateSparkJarDevelopmentKitRequest request) {
        return sparkJarDevelopmentKitService.generate(id, request);
    }

    @GetMapping("/{id}/spark-jar-development-kit/artifact")
    @PreAuthorize("hasAuthority('task.update') and hasAuthority('model.view') and hasAuthority('datasource.metadata')")
    @Operation(summary = "下载当前 Spark JAR 本地开发包")
    public ResponseEntity<StreamingResponseBody> downloadSparkJarDevelopmentKit(@PathVariable UUID id) {
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

    @GetMapping("/{id}/model-relations")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询当前保存任务定义引用的模型")
    public TaskModelRelationsResponse getModelRelations(@PathVariable UUID id) {
        return taskModelRelationQueryService.getTaskModelRelations(id);
    }

    @GetMapping("/{id}/lineage/table")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务当前表级血缘")
    public TaskLineageGraphResponse getTableLineage(
            @PathVariable UUID id,
            @RequestParam(required = false) String flowKey
    ) {
        return taskLineageQueryService.table(id, flowKey);
    }

    @GetMapping("/{id}/lineage/fields")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务当前字段级血缘")
    public TaskLineageGraphResponse getFieldLineage(
            @PathVariable UUID id,
            @RequestParam(required = false) String flowKey,
            @RequestParam(required = false) String outputFieldKey
    ) {
        return taskLineageQueryService.fields(id, flowKey, outputFieldKey);
    }

    @PostMapping("/{id}/lineage/actions/query-fields")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "批量查询任务当前输出链路的字段级血缘")
    public TaskFieldLineageGraphResponse queryFieldLineage(
            @PathVariable UUID id,
            @Valid @RequestBody QueryTaskFieldLineageRequest request
    ) {
        return taskLineageQueryService.fieldLines(id, request.flowKey(), request.outputFieldKeys());
    }

    @GetMapping("/{id}/streaming-configuration")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询实时任务配置")
    public TaskStreamingConfigurationResponse getStreamingConfiguration(@PathVariable UUID id) {
        return streamingService.getConfiguration(id);
    }

    @GetMapping("/{id}/streaming-status")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询实时任务运行状态")
    public TaskStreamingStatusResponse getStreamingStatus(@PathVariable UUID id) {
        return streamingService.status(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task.create')")
    @Operation(summary = "创建任务")
    public DataTaskResponse create(@Valid @RequestBody CreateDataTaskRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "修改任务基本信息")
    public DataTaskResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDataTaskRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/update-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "整体保存本地 SQL 任务定义")
    public LocalSqlTaskDefinitionResponse updateDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLocalSqlTaskDefinitionRequest request
    ) {
        return service.updateDefinition(id, request);
    }

    @PostMapping("/{id}/actions/update-canvas-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "整体保存 Spark Canvas 任务定义")
    public CanvasTaskDefinitionResponse updateCanvasDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCanvasTaskDefinitionRequest request
    ) {
        return canvasDefinitionService.update(id, request);
    }

    @PostMapping("/{id}/actions/update-model-quality-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "保存 Spark 模型质检任务定义")
    public ModelQualityTaskDefinitionResponse updateModelQualityDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateModelQualityTaskDefinitionRequest request
    ) {
        return modelQualityTaskDefinitionService.update(id, request);
    }

    @PostMapping("/{id}/actions/update-spark-jar-definition")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "保存 Spark JAR 任务定义")
    public SparkJarTaskDefinitionResponse updateSparkJarDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSparkJarTaskDefinitionRequest request
    ) {
        return sparkJarTaskDefinitionService.update(id, request);
    }

    @PostMapping(value = "/{id}/actions/upload-spark-jar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "上传或覆盖 Spark JAR")
    public SparkJarTaskDefinitionResponse uploadSparkJar(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file
    ) {
        return sparkJarTaskDefinitionService.upload(id, file);
    }

    @PostMapping("/{id}/actions/update-streaming-configuration")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "修改实时任务配置")
    public TaskStreamingConfigurationResponse updateStreamingConfiguration(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskStreamingConfigurationRequest request
    ) {
        return streamingService.updateConfiguration(id, request);
    }

    @PostMapping("/{id}/actions/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "启动或恢复实时任务")
    public TaskStreamingStatusResponse startStreaming(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) StartStreamingTaskRequest request
    ) {
        return streamingService.start(id, request == null ? null : request.checkpointMode());
    }

    @PostMapping("/{id}/actions/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "正常停止实时任务")
    public TaskStreamingStatusResponse stopStreaming(@PathVariable UUID id) {
        return streamingService.stop(id);
    }

    @PostMapping("/{id}/actions/validate-definition")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "校验本地 SQL 任务定义")
    public LocalSqlDefinitionValidationResponse validateDefinition(@PathVariable UUID id) {
        return service.validateDefinition(id);
    }

    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "发布任务")
    public DataTaskResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "停用任务")
    public DataTaskResponse disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "重新启用任务")
    public DataTaskResponse enable(@PathVariable UUID id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task.delete')")
    @Operation(summary = "删除未发布任务")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
