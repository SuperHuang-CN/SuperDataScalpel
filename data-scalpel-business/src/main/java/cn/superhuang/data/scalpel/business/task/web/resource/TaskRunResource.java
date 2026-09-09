package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.task.web.response.WorkflowRunResponse;

import cn.superhuang.data.scalpel.business.task.service.WorkflowRunService;

import cn.superhuang.data.scalpel.business.task.service.TaskRunService;
import cn.superhuang.data.scalpel.business.task.service.TaskStreamingService;
import cn.superhuang.data.scalpel.business.task.service.TaskRunService.TaskRunArtifact;
import cn.superhuang.data.scalpel.business.task.service.TaskRunService.TaskRunArtifactStream;
import cn.superhuang.data.scalpel.business.task.service.QualityFailureSampleService;
import cn.superhuang.data.scalpel.business.task.web.response.QualityFailureSampleResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunLineageResponse;
import cn.superhuang.data.scalpel.business.task.web.response.SparkJarTrialPreviewResponse;
import cn.superhuang.data.scalpel.business.task.web.response.CanvasTrialPreviewResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunArtifactsResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunLogResponse;
import cn.superhuang.data.scalpel.business.task.service.SparkJarLineageQueryService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.nio.charset.StandardCharsets;

@RestController
@Tag(name = "任务运行")
public class TaskRunResource {

    private final WorkflowRunService workflows;
    private final TaskRunService service;
    private final QualityFailureSampleService qualityFailureSampleService;
    private final SparkJarLineageQueryService sparkJarLineageQueryService;
    private final TaskStreamingService taskStreamingService;

    public TaskRunResource(WorkflowRunService workflows, TaskRunService service, QualityFailureSampleService qualityFailureSampleService,
                           SparkJarLineageQueryService sparkJarLineageQueryService,
                           TaskStreamingService taskStreamingService) {
        this.workflows = workflows;
        this.service = service;
        this.qualityFailureSampleService = qualityFailureSampleService;
        this.sparkJarLineageQueryService = sparkJarLineageQueryService;
        this.taskStreamingService = taskStreamingService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "异步运行已发布任务", prerequisites = "任务必须满足原业务发布和运行条件；202 表示已受理，使用返回的运行 ID 查询状态，不要重复提交。", relatedOperations = {"GET /api/v1/tasks/{id}", "GET /api/v1/tasks/{id}/runs"})
    @PostMapping("/api/v1/tasks/{id}/actions/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "异步运行已发布任务")
    public TaskRunResponse run(@PathVariable UUID id) {
        return service.run(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行历史")
    @GetMapping("/api/v1/tasks/{id}/runs")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行历史")
    public PageResponse<TaskRunResponse> search(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行详情")
    @GetMapping("/api/v1/task-runs/{runId}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行详情")
    public TaskRunResponse get(@PathVariable UUID runId) {
        return service.get(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询工作流运行图与子运行")
    @GetMapping("/api/v1/task-runs/{runId}/workflow")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询工作流运行图与子运行")
    public WorkflowRunResponse workflow(@PathVariable UUID runId) {
        return workflows.get(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Spark JAR 运行血缘摄取状态")
    @GetMapping("/api/v1/task-runs/{runId}/lineage")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark JAR 运行血缘摄取状态")
    public TaskRunLineageResponse lineage(@PathVariable UUID runId) {
        return sparkJarLineageQueryService.get(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Spark JAR 在线试运行输出预览")
    @GetMapping("/api/v1/task-runs/{runId}/trial-preview")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark JAR 在线试运行输出预览")
    public SparkJarTrialPreviewResponse trialPreview(@PathVariable UUID runId) {
        return service.trialPreview(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Canvas 节点试运行数据预览")
    @GetMapping("/api/v1/task-runs/{runId}/canvas-trial-preview")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Canvas 节点试运行数据预览")
    public CanvasTrialPreviewResponse canvasTrialPreview(@PathVariable UUID runId) {
        return service.canvasTrialPreview(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载任务运行结果")
    @GetMapping("/api/v1/task-runs/{runId}/artifacts/result")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "下载任务运行结果")
    public ResponseEntity<StreamingResponseBody> resultArtifact(@PathVariable UUID runId) {
        return artifactResponse(service.openArtifact(runId, "result"));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载任务运行日志")
    @GetMapping("/api/v1/task-runs/{runId}/artifacts/log")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "下载任务运行日志")
    public ResponseEntity<StreamingResponseBody> logArtifact(@PathVariable UUID runId) {
        return artifactResponse(service.openArtifact(runId, "log"));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行制品元数据")
    @GetMapping("/api/v1/task-runs/{runId}/artifacts")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行制品元数据")
    public TaskRunArtifactsResponse artifacts(@PathVariable UUID runId) {
        return service.artifacts(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行中的控制台日志窗口")
    @GetMapping("/api/v1/task-runs/{runId}/logs")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行中的控制台日志窗口")
    public TaskRunLogResponse logs(@PathVariable UUID runId) {
        return service.logs(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "在线预览任务运行制品")
    @GetMapping("/api/v1/task-runs/{runId}/artifacts/{kind}/preview")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "在线预览任务运行制品")
    public ResponseEntity<byte[]> previewArtifact(@PathVariable UUID runId, @PathVariable String kind) {
        return previewResponse(service.previewArtifact(runId, kind));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "预览模型质检规则失败样本")
    @GetMapping("/api/v1/task-runs/{runId}/quality-rules/{ruleId}/samples")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "预览模型质检规则失败样本")
    public QualityFailureSampleResponse qualitySamples(
            @PathVariable UUID runId,
            @PathVariable UUID ruleId
    ) {
        return qualityFailureSampleService.preview(runId, ruleId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载模型质检规则失败样本 Parquet")
    @GetMapping("/api/v1/task-runs/{runId}/quality-rules/{ruleId}/samples/download")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "下载模型质检规则失败样本 Parquet")
    public ResponseEntity<byte[]> downloadQualitySamples(
            @PathVariable UUID runId,
            @PathVariable UUID ruleId
    ) {
        QualityFailureSampleService.QualitySampleDownload download =
                qualityFailureSampleService.download(runId, ruleId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.apache.parquet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(download.content());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "取消任务运行")
    @PostMapping("/api/v1/task-runs/{runId}/actions/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "取消任务运行")
    public TaskRunResponse cancel(@PathVariable UUID runId) {
        return service.cancel(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "正常停止 Spark 实时 JAR 在线试运行")
    @PostMapping("/api/v1/task-runs/{runId}/actions/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "正常停止 Spark 实时 JAR 在线试运行")
    public TaskRunResponse stop(@PathVariable UUID runId) {
        return taskStreamingService.stopTrial(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "强制终止 Spark 任务运行")
    @PostMapping("/api/v1/task-runs/{runId}/actions/force-terminate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "强制终止 Spark 任务运行")
    public TaskRunResponse forceTerminate(@PathVariable UUID runId) {
        return service.forceTerminate(runId);
    }

    private static ResponseEntity<StreamingResponseBody> artifactResponse(TaskRunArtifactStream artifact) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .contentLength(artifact.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(artifact.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(output -> {
                    try (artifact) {
                        artifact.content().inputStream().transferTo(output);
                    }
                });
    }

    private static ResponseEntity<byte[]> previewResponse(TaskRunArtifact artifact) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .contentLength(artifact.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(artifact.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(artifact.content());
    }

}
