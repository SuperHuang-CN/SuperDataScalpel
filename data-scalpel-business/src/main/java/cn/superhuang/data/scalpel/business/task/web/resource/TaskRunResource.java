package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.task.service.TaskRunService;
import cn.superhuang.data.scalpel.business.task.service.TaskRunService.TaskRunArtifact;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "任务运行")
public class TaskRunResource {

    private final TaskRunService service;

    public TaskRunResource(TaskRunService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/tasks/{id}/actions/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "异步运行已发布任务")
    public TaskRunResponse run(@PathVariable UUID id) {
        return service.run(id);
    }

    @GetMapping("/api/v1/tasks/{id}/runs")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行历史")
    public PageResponse<TaskRunResponse> search(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(id, request);
    }

    @GetMapping("/api/v1/task-runs/{runId}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行详情")
    public TaskRunResponse get(@PathVariable UUID runId) {
        return service.get(runId);
    }

    @GetMapping("/api/v1/task-runs/{runId}/artifacts/result")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "读取 Canvas 任务运行结果")
    public ResponseEntity<byte[]> resultArtifact(@PathVariable UUID runId) {
        return artifactResponse(service.resultArtifact(runId));
    }

    @GetMapping("/api/v1/task-runs/{runId}/artifacts/log")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "读取 Canvas 任务运行日志")
    public ResponseEntity<byte[]> logArtifact(@PathVariable UUID runId) {
        return artifactResponse(service.logArtifact(runId));
    }

    @PostMapping("/api/v1/task-runs/{runId}/actions/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.execute')")
    @Operation(summary = "取消 Canvas 任务运行")
    public TaskRunResponse cancel(@PathVariable UUID runId) {
        return service.cancel(runId);
    }

    private static ResponseEntity<byte[]> artifactResponse(TaskRunArtifact artifact) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.fileName() + "\"")
                .body(artifact.content());
    }

}
