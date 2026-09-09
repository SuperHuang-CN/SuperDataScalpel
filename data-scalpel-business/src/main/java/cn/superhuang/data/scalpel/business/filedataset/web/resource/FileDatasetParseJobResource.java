package cn.superhuang.data.scalpel.business.filedataset.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.filedataset.service.queue.FileDatasetParseJobMonitoringService;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParseJobResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParseQueueSummaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/file-dataset-parse-jobs")
@Tag(name = "文件数据集解析队列监控")
public class FileDatasetParseJobResource {

    private final FileDatasetParseJobMonitoringService service;

    public FileDatasetParseJobResource(FileDatasetParseJobMonitoringService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "分页查询文件数据集解析任务（只读）")
    @GetMapping
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "分页查询文件数据集解析任务（只读）")
    public PageResponse<FileDatasetParseJobResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询文件数据集解析队列摘要（只读）")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集解析队列摘要（只读）")
    public FileDatasetParseQueueSummaryResponse summary() {
        return service.summary();
    }
}
