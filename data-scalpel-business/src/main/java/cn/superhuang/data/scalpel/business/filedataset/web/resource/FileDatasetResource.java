package cn.superhuang.data.scalpel.business.filedataset.web.resource;

import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetContent;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetService;
import cn.superhuang.data.scalpel.business.filedataset.web.request.CreateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetTableRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetFileResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetPreviewResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetSchemaResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableLoadSubmissionResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableSourceResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetUploadResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/file-datasets")
@Tag(name = "文件数据集管理")
public class FileDatasetResource {

    private final FileDatasetService service;

    public FileDatasetResource(FileDatasetService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集")
    public PageResponse<FileDatasetResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集详情")
    public FileDatasetResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('filedataset.create')")
    @Operation(summary = "创建空文件数据集")
    public FileDatasetResponse create(@Valid @RequestBody CreateFileDatasetRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "修改文件数据集信息和共享解析参数")
    public FileDatasetResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFileDatasetRequest request
    ) {
        return service.update(id, request);
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集的物理文件")
    public PageResponse<FileDatasetFileResponse> files(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.searchFiles(id, request);
    }

    @PostMapping(path = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "向文件数据集上传一个文件并创建逻辑表")
    public FileDatasetUploadResponse uploadFiles(
            @PathVariable UUID id,
            @RequestPart("files") List<MultipartFile> files
    ) {
        return service.uploadFiles(id, files);
    }

    @PostMapping(path = "/{id}/files/{fileId}/actions/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "替换物理文件并重建其逻辑表")
    public FileDatasetUploadResponse replaceFile(
            @PathVariable UUID id,
            @PathVariable UUID fileId,
            @RequestPart("file") MultipartFile file
    ) {
        return service.replaceFile(id, fileId, file);
    }

    @PostMapping("/{id}/files/{fileId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "删除物理文件及其逻辑表")
    public void deleteFile(@PathVariable UUID id, @PathVariable UUID fileId) {
        service.deleteFile(id, fileId);
    }

    @GetMapping("/{id}/files/{fileId}/content")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "下载文件数据集物理文件")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID id, @PathVariable UUID fileId) {
        FileDatasetContent content = service.openContent(id, fileId);
        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = content.inputStream()) {
                inputStream.transferTo(outputStream);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(toMediaType(content.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(
                                content.originalFileName(), StandardCharsets.UTF_8
                        ).build().toString()
                );
        if (content.sizeBytes() >= 0) {
            response.contentLength(content.sizeBytes());
        }
        return response.body(body);
    }

    @GetMapping("/{id}/tables")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集的逻辑表")
    public PageResponse<FileDatasetTableResponse> tables(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.searchTables(id, request);
    }

    @GetMapping("/{id}/tables/{tableId}")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集逻辑表详情")
    public FileDatasetTableResponse table(@PathVariable UUID id, @PathVariable UUID tableId) {
        return service.getTable(id, tableId);
    }

    @PostMapping("/{id}/tables/{tableId}/actions/update")
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "修改逻辑表名称")
    public FileDatasetTableResponse updateTable(
            @PathVariable UUID id,
            @PathVariable UUID tableId,
            @Valid @RequestBody UpdateFileDatasetTableRequest request
    ) {
        return service.updateTable(id, tableId, request);
    }

    @GetMapping("/{id}/tables/{tableId}/sources")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询逻辑表的数据来源")
    public List<FileDatasetTableSourceResponse> tableSources(
            @PathVariable UUID id,
            @PathVariable UUID tableId
    ) {
        return service.tableSources(id, tableId);
    }

    @PostMapping(
            path = "/{id}/tables/{tableId}/actions/append",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "向逻辑表追加一个文件来源")
    public FileDatasetTableLoadSubmissionResponse append(
            @PathVariable UUID id,
            @PathVariable UUID tableId,
            @RequestPart("file") MultipartFile file
    ) {
        return service.append(id, tableId, file);
    }

    @PostMapping(
            path = "/{id}/tables/{tableId}/actions/replace-data",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "全量覆盖逻辑表的全部当前来源")
    public FileDatasetTableLoadSubmissionResponse replaceData(
            @PathVariable UUID id,
            @PathVariable UUID tableId,
            @RequestPart("file") MultipartFile file
    ) {
        return service.replaceData(id, tableId, file);
    }

    @PostMapping(
            path = "/{id}/tables/{tableId}/sources/{sourceId}/actions/replace",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "替换逻辑表中的一个当前来源")
    public FileDatasetTableLoadSubmissionResponse replaceSource(
            @PathVariable UUID id,
            @PathVariable UUID tableId,
            @PathVariable UUID sourceId,
            @RequestPart("file") MultipartFile file
    ) {
        return service.replaceSource(id, tableId, sourceId, file);
    }

    @PostMapping("/{id}/tables/{tableId}/sources/{sourceId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "删除表数据来源")
    public void deleteSource(
            @PathVariable UUID id,
            @PathVariable UUID tableId,
            @PathVariable UUID sourceId
    ) {
        service.deleteSource(id, tableId, sourceId);
    }

    @GetMapping("/{id}/tables/{tableId}/schema")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询逻辑表 Schema")
    public FileDatasetSchemaResponse schema(@PathVariable UUID id, @PathVariable UUID tableId) {
        return service.schema(id, tableId);
    }

    @GetMapping("/{id}/tables/{tableId}/preview")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "预览已解析逻辑表")
    public FileDatasetPreviewResponse preview(
            @PathVariable UUID id,
            @PathVariable UUID tableId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return service.preview(id, tableId, limit);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('filedataset.delete')")
    @Operation(summary = "删除文件数据集及其全部文件和表")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    private static MediaType toMediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
