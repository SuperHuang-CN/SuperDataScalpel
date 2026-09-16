package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.task.web.response.WorkflowRunResponse;

import cn.superhuang.data.scalpel.business.task.service.WorkflowRunService;

import cn.superhuang.data.scalpel.business.task.service.TaskRunService;
import cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactQueryService;
import cn.superhuang.data.scalpel.business.task.service.TaskStreamingService;
import cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactQueryService.TaskRunArtifact;
import cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactQueryService.TaskRunArtifactStream;
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
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "异步运行已发布批任务或工作流", description = "为已发布且可运行的 LOCAL_SQL、SPARK_CANVAS、SPARK_MODEL_QUALITY、SPARK_JAR 或 WORKFLOW 创建运行并返回运行 UUID。两类实时任务必须使用任务 start 接口。202 表示已受理，应通过运行详情继续查询最终状态；相同请求没有通用幂等键。")
    public TaskRunResponse run(@Parameter(description = "待运行或查询历史的任务 UUID。") @PathVariable UUID id) {
        return service.run(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行历史")
    @GetMapping("/api/v1/tasks/{id}/runs")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行历史", description = "分页查询指定任务的历史运行、执行模式、状态和时间信息，不读取大体积日志或制品正文。")
    public PageResponse<TaskRunResponse> search(
            @Parameter(description = "待运行或查询历史的任务 UUID。") @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行详情")
    @GetMapping("/api/v1/task-runs/{runId}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行详情", description = "读取一次任务运行的当前状态、时间、错误摘要和结果引用；运行中状态可能继续变化。")
    public TaskRunResponse get(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return service.get(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询工作流运行图与子运行",
            prerequisites = "runId 必须属于一次 WORKFLOW 父运行。",
            relatedOperations = {"GET /api/v1/task-runs/{runId}", "GET /api/v1/tasks/{id}/workflow-definition"})
    @GetMapping("/api/v1/task-runs/{runId}/workflow")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询工作流运行图与子运行", description = "仅接受 WORKFLOW 父运行。读取本次运行固定的定义快照、节点依赖、父运行状态和已创建的子运行；尚未触发或因上游失败、取消、跳过而未执行的节点可能没有 childRun，并通过投影 status 表达。不触发或修改工作流。")
    public WorkflowRunResponse workflow(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return workflows.get(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Spark JAR 运行血缘摄取状态")
    @GetMapping("/api/v1/task-runs/{runId}/lineage")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询批处理 Spark JAR 运行血缘摄取状态", description = "仅适用于 SPARK_JAR 正式或试运行，读取用户作业上报血缘的独立摄取状态、覆盖度、告警和错误；运行未结束且尚无摄取记录时为 PENDING，终态仍无结果时为 NOT_AVAILABLE，不从任务定义推断本次运行血缘。")
    public TaskRunLineageResponse lineage(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return sparkJarLineageQueryService.get(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Spark JAR 在线试运行输出预览",
            prerequisites = "runId 必须属于 executionMode=TRIAL 的 SPARK_JAR 或 SPARK_STREAMING_JAR 运行。",
            relatedOperations = {"GET /api/v1/task-runs/{runId}", "POST /api/v1/tasks/{id}/spark-jar-online-source/actions/trial-run"})
    @GetMapping("/api/v1/task-runs/{runId}/trial-preview")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark JAR 在线试运行输出预览", description = "仅适用于 executionMode=TRIAL 的批处理或流式 Spark JAR。活动运行读取尝试级最新快照，终态优先读取并校验 result 制品；source=NONE 表示尚无可读预览。预览最多包含 20 次受控写入、每次最多 100 行，试运行不会向正式目标写入。")
    public SparkJarTrialPreviewResponse trialPreview(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return service.trialPreview(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 Canvas 节点试运行数据预览",
            prerequisites = "runId 必须属于 executionMode=TRIAL 的 SPARK_CANVAS；活动状态下 preview 固定为空，应等待终态后再读。",
            relatedOperations = {"GET /api/v1/task-runs/{runId}", "POST /api/v1/tasks/{id}/canvas-definition/actions/trial-run"})
    @GetMapping("/api/v1/task-runs/{runId}/canvas-trial-preview")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Canvas 节点试运行数据预览", description = "仅适用于 executionMode=TRIAL 的 SPARK_CANVAS。QUEUED、RUNNING、CANCEL_REQUESTED 或 STOP_REQUESTED 时只返回状态且 preview 为空；终态从受控 result 制品读取目标表 Schema、最多 100 行且最多 4 MiB 的 JSON 对象字符串和告警。失败或取消终态也可能没有预览。")
    public CanvasTrialPreviewResponse canvasTrialPreview(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return service.canvasTrialPreview(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载任务运行结果")
    @GetMapping("/api/v1/task-runs/{runId}/artifacts/result")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "下载任务运行结果", description = "以流式二进制响应下载该运行登记的结果制品；持续流和文件下载不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<StreamingResponseBody> resultArtifact(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return artifactResponse(service.openArtifact(runId, "result"));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载任务运行日志")
    @GetMapping("/api/v1/task-runs/{runId}/artifacts/log")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "下载任务运行日志", description = "以流式二进制响应下载该运行的完整日志制品；持续流和文件下载不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<StreamingResponseBody> logArtifact(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return artifactResponse(service.openArtifact(runId, "log"));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行制品元数据",
            prerequisites = "runId 必须属于非 LOCAL_SQL 运行；返回元数据不代表 result 或 log 已生成。",
            relatedOperations = {"GET /api/v1/task-runs/{runId}", "GET /api/v1/task-runs/{runId}/logs"})
    @GetMapping("/api/v1/task-runs/{runId}/artifacts")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行制品元数据", description = "返回非 LOCAL_SQL 运行的 result 与 log 制品存在性、固定下载文件名、大小和能否在线预览，不读取正文；存储元数据读取失败时通过 SIZE_UNAVAILABLE 表达，不把接口整体报错。")
    public TaskRunArtifactsResponse artifacts(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return service.artifacts(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行中的控制台日志窗口",
            prerequisites = "runId 必须属于需要计算引擎的 Spark 任务；返回 content 可能尚未产生或被截断。",
            relatedOperations = {"GET /api/v1/task-runs/{runId}", "GET /api/v1/task-runs/{runId}/artifacts"})
    @GetMapping("/api/v1/task-runs/{runId}/logs")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询 Spark 任务运行日志窗口", description = "仅适用于需要计算引擎的运行。活动运行从 Dispatcher 读取窗口；终态优先读取归档制品，超过 1 MiB 时最多返回最近 2,000 行且不超过 1 MiB。该接口不保证包含完整日志。")
    public TaskRunLogResponse logs(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return service.logs(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "在线预览任务运行制品")
    @GetMapping("/api/v1/task-runs/{runId}/artifacts/{kind}/preview")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "在线预览任务运行制品", description = "以内联二进制响应完整返回 result 或 log 制品；只允许大小不超过 1 MiB 的已生成制品，超过上限返回 413。文件响应不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> previewArtifact(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId, @Parameter(description = "制品种类，仅允许当前运行声明可预览的 result 或 log。") @PathVariable String kind) {
        return previewResponse(service.previewArtifact(runId, kind));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "预览模型质检规则失败样本")
    @GetMapping("/api/v1/task-runs/{runId}/quality-rules/{ruleId}/samples")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "预览模型质检规则失败样本", description = "仅适用于 status=SUCCESS 的 SPARK_MODEL_QUALITY 运行，以及该次结果中 state=FAILED 且 sample.status=AVAILABLE 的规则。校验并解析不超过 20 MiB 的 Parquet 样本，返回 1 到 1000 行及截断信息，不重新执行质检。")
    public QualityFailureSampleResponse qualitySamples(
            @Parameter(description = "任务运行 UUID。") @PathVariable UUID runId,
            @Parameter(description = "质量规则 UUID，必须属于本次质量任务运行。") @PathVariable UUID ruleId
    ) {
        return qualityFailureSampleService.preview(runId, ruleId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载模型质检规则失败样本 Parquet")
    @GetMapping("/api/v1/task-runs/{runId}/quality-rules/{ruleId}/samples/download")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "下载模型质检规则失败样本 Parquet", description = "按与预览相同的运行和规则条件，校验大小、SHA-256 与结果描述后下载原始 Parquet 样本，最大 20 MiB；文件响应不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> downloadQualitySamples(
            @Parameter(description = "任务运行 UUID。") @PathVariable UUID runId,
            @Parameter(description = "质量规则 UUID，必须属于本次质量任务运行。") @PathVariable UUID ruleId
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
    @Operation(summary = "取消任务运行", description = "取消 WORKFLOW、LOCAL_SQL，或已创建外部执行的 SPARK_CANVAS、SPARK_MODEL_QUALITY、SPARK_JAR 批运行。CANCEL_REQUESTED 时幂等返回，其他终态拒绝；实时任务与实时试运行分别使用任务 stop 或运行 stop。202 表示已受理，最终状态需继续查询。")
    public TaskRunResponse cancel(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return service.cancel(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "正常停止 Spark 实时 JAR 在线试运行")
    @PostMapping("/api/v1/task-runs/{runId}/actions/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "正常停止 Spark 实时 JAR 在线试运行", description = "仅适用于 executionMode=TRIAL 的 SPARK_STREAMING_JAR。请求正常停止当前外部执行；已为 STOP_REQUESTED 或 STOPPED 时幂等返回，其他已结束状态拒绝。202 表示停止命令已入队，最终状态需继续查询。")
    public TaskRunResponse stop(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
        return taskStreamingService.stopTrial(runId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "强制终止 Spark 任务运行")
    @PostMapping("/api/v1/task-runs/{runId}/actions/force-terminate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "强制终止 Spark 任务运行", description = "仅适用于已经先进入 CANCEL_REQUESTED 或 STOP_REQUESTED、且存在外部执行标识的计算引擎任务。命令不会再次改变当前状态，可能来不及执行作业清理逻辑；202 表示命令已入队，最终状态需继续查询。")
    public TaskRunResponse forceTerminate(@Parameter(description = "任务运行 UUID。") @PathVariable UUID runId) {
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
