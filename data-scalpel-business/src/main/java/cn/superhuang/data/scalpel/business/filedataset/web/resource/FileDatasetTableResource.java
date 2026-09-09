package cn.superhuang.data.scalpel.business.filedataset.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetService;
import cn.superhuang.data.scalpel.business.filedataset.web.request.QueryFileDatasetCanvasMetadataRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetCanvasMetadataResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/file-dataset-tables")
@Tag(name = "文件数据集表")
public class FileDatasetTableResource {

    private final FileDatasetService service;

    public FileDatasetTableResource(FileDatasetService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "批量查询 Canvas 所需的文件表元数据")
    @PostMapping("/actions/query-canvas-metadata")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "批量查询 Canvas 所需的文件表元数据", description = "只读操作")
    public FileDatasetCanvasMetadataResponse queryCanvasMetadata(
            @Valid @RequestBody QueryFileDatasetCanvasMetadataRequest request
    ) {
        return service.queryCanvasMetadata(request.fileDatasetTableIds());
    }
}
