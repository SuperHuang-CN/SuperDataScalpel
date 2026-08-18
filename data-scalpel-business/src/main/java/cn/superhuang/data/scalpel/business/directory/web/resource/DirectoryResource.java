package cn.superhuang.data.scalpel.business.directory.web.resource;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryExcelFile;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryExcelService;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.directory.web.request.CreateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.request.UpdateDirectoryRequest;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryResponse;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryImportResultResponse;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryTreeNodeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directories")
@Tag(name = "目录管理")
public class DirectoryResource {

    private final DirectoryService service;
    private final DirectoryExcelService excelService;

    public DirectoryResource(DirectoryService service, DirectoryExcelService excelService) {
        this.service = service;
        this.excelService = excelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('directory.view')")
    @Operation(summary = "查询目录树")
    public List<DirectoryTreeNodeResponse> tree(@RequestParam DirectoryScope scope) {
        return service.tree(scope);
    }

    @GetMapping("/actions/download-import-template")
    @PreAuthorize("hasAuthority('directory.view')")
    @Operation(summary = "下载目录导入模板")
    public ResponseEntity<byte[]> downloadImportTemplate() {
        return excelFile(excelService.template());
    }

    @GetMapping("/actions/export")
    @PreAuthorize("hasAuthority('directory.view')")
    @Operation(summary = "导出指定范围的完整目录树")
    public ResponseEntity<byte[]> export(@RequestParam DirectoryScope scope) {
        return excelFile(excelService.export(scope));
    }

    @PostMapping(path = "/actions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('directory.manage')")
    @Operation(summary = "原子校验并导入指定范围的目录树")
    public DirectoryImportResultResponse importDirectories(
            @RequestParam DirectoryScope scope,
            @RequestPart("file") MultipartFile file
    ) {
        return excelService.importDirectories(scope, file);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('directory.view')")
    @Operation(summary = "查询目录详情")
    public DirectoryResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('directory.manage')")
    @Operation(summary = "新增目录")
    public DirectoryResponse create(@Valid @RequestBody CreateDirectoryRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('directory.manage')")
    @Operation(summary = "修改目录")
    public DirectoryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDirectoryRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('directory.manage')")
    @Operation(summary = "删除目录")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    private ResponseEntity<byte[]> excelFile(DirectoryExcelFile file) {
        byte[] content = file.content();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(DirectoryExcelService.CONTENT_TYPE))
                .contentLength(content.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(content);
    }
}
