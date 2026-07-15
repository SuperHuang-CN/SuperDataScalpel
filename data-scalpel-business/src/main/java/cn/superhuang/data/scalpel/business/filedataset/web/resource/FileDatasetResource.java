package cn.superhuang.data.scalpel.business.filedataset.web.resource;

import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetContent;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetService;
import cn.superhuang.data.scalpel.business.filedataset.web.request.CreateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.ConfigureFileDatasetParsingRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.ReplaceFileDatasetContentRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParsingResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetPreviewResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
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

    @GetMapping("/{id}/parsing")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "查询文件数据集解析配置")
    public FileDatasetParsingResponse parsing(@PathVariable UUID id) {
        return service.parsing(id);
    }

    @GetMapping("/{id}/preview")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "预览已解析文件数据集")
    public FileDatasetPreviewResponse preview(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        return service.preview(id, limit);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('filedataset.create')")
    @Operation(summary = "上传并新增文件数据集")
    public FileDatasetResponse create(
            @Valid @RequestPart("request") CreateFileDatasetRequest request,
            @RequestPart("file") MultipartFile file
    ) {
        return service.create(request, file);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "修改文件数据集信息")
    public FileDatasetResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFileDatasetRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/configure-parsing")
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "保存文件数据集解析参数")
    public FileDatasetParsingResponse configureParsing(
            @PathVariable UUID id,
            @Valid @RequestBody ConfigureFileDatasetParsingRequest request
    ) {
        return service.configureParsing(id, request);
    }

    @PostMapping("/{id}/actions/parse")
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "执行文件数据集抽样解析")
    public FileDatasetParsingResponse parse(@PathVariable UUID id) {
        return service.parse(id);
    }

    @PostMapping(path = "/{id}/actions/replace-content", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('filedataset.update')")
    @Operation(summary = "替换文件数据集内容")
    public FileDatasetResponse replaceContent(
            @PathVariable UUID id,
            @Valid @RequestPart("request") ReplaceFileDatasetContentRequest request,
            @RequestPart("file") MultipartFile file
    ) {
        return service.replaceContent(id, request, file);
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("hasAuthority('filedataset.view')")
    @Operation(summary = "下载文件数据集原始内容")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID id) {
        FileDatasetContent content = service.openContent(id);
        StreamingResponseBody body = outputStream -> {
            try (InputStream inputStream = content.inputStream()) {
                inputStream.transferTo(outputStream);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(toMediaType(content.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(content.originalFileName(), StandardCharsets.UTF_8).build().toString()
                );
        if (content.sizeBytes() >= 0) {
            response.contentLength(content.sizeBytes());
        }
        return response.body(body);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('filedataset.delete')")
    @Operation(summary = "删除文件数据集及其存储内容")
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
