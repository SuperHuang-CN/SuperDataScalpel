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
    @Operation(summary = "批量查询 Canvas 所需的文件表元数据", description = "按请求顺序返回仍存在且来源关系完整的文件数据集逻辑表、解析状态、文件准备状态和字段 Schema；缺失或内部关系不完整的 ID 不会出现在结果中，不读取文件正文。")
    public FileDatasetCanvasMetadataResponse queryCanvasMetadata(
            @Valid @RequestBody QueryFileDatasetCanvasMetadataRequest request
    ) {
        return service.queryCanvasMetadata(request.fileDatasetTableIds());
    }
}
