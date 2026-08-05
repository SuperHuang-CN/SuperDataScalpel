package cn.superhuang.data.scalpel.business.standard.web.resource;

import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryExcelFile;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryExcelService;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryService;
import cn.superhuang.data.scalpel.business.standard.web.request.CreateStandardDictionaryItemRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.CreateStandardDictionaryRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.ExportStandardDictionaryMetadataRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.MoveStandardDictionaryItemRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.StandardDictionaryVersionRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.UpdateStandardDictionaryItemRequest;
import cn.superhuang.data.scalpel.business.standard.web.request.UpdateStandardDictionaryRequest;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryDetailResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryFieldReferenceResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryImportPreviewResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryImportResultResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryItemMutationResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryItemTreeResponse;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/standard-dictionaries")
@Tag(name = "数据标准码表管理")
public class StandardDictionaryResource {

    private final StandardDictionaryService service;
    private final StandardDictionaryExcelService excelService;

    public StandardDictionaryResource(
            StandardDictionaryService service,
            StandardDictionaryExcelService excelService
    ) {
        this.service = service;
        this.excelService = excelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "分页查询码表")
    public PageResponse<StandardDictionaryResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "查询码表详情")
    public StandardDictionaryDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "新增码表")
    public StandardDictionaryDetailResponse create(
            @Valid @RequestBody CreateStandardDictionaryRequest request
    ) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "修改码表")
    public StandardDictionaryDetailResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStandardDictionaryRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "启用码表")
    public StandardDictionaryDetailResponse enable(
            @PathVariable UUID id,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        return service.enable(id, request);
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "停用码表")
    public StandardDictionaryDetailResponse disable(
            @PathVariable UUID id,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        return service.disable(id, request);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "删除未被模型字段引用的码表")
    public void delete(
            @PathVariable UUID id,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        service.delete(id, request);
    }

    @GetMapping("/{dictionaryId}/items/tree")
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "查询码表项树")
    public List<StandardDictionaryItemTreeResponse> tree(@PathVariable UUID dictionaryId) {
        return service.tree(dictionaryId);
    }

    @PostMapping("/{dictionaryId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "新增码表树节点")
    public StandardDictionaryItemMutationResponse createItem(
            @PathVariable UUID dictionaryId,
            @Valid @RequestBody CreateStandardDictionaryItemRequest request
    ) {
        return service.createItem(dictionaryId, request);
    }

    @PostMapping("/{dictionaryId}/items/{itemId}/actions/update")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "修改码表树节点")
    public StandardDictionaryItemMutationResponse updateItem(
            @PathVariable UUID dictionaryId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateStandardDictionaryItemRequest request
    ) {
        return service.updateItem(dictionaryId, itemId, request);
    }

    @PostMapping("/{dictionaryId}/items/{itemId}/actions/move")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "移动码表树节点")
    public StandardDictionaryItemMutationResponse moveItem(
            @PathVariable UUID dictionaryId,
            @PathVariable UUID itemId,
            @Valid @RequestBody MoveStandardDictionaryItemRequest request
    ) {
        return service.moveItem(dictionaryId, itemId, request);
    }

    @PostMapping("/{dictionaryId}/items/{itemId}/actions/enable")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "启用码表树节点")
    public StandardDictionaryItemMutationResponse enableItem(
            @PathVariable UUID dictionaryId,
            @PathVariable UUID itemId,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        return service.enableItem(dictionaryId, itemId, request);
    }

    @PostMapping("/{dictionaryId}/items/{itemId}/actions/disable")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "停用码表树节点")
    public StandardDictionaryItemMutationResponse disableItem(
            @PathVariable UUID dictionaryId,
            @PathVariable UUID itemId,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        return service.disableItem(dictionaryId, itemId, request);
    }

    @PostMapping("/{dictionaryId}/items/{itemId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "删除码表树节点")
    public void deleteItem(
            @PathVariable UUID dictionaryId,
            @PathVariable UUID itemId,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        service.deleteItem(dictionaryId, itemId, request);
    }

    @GetMapping("/{id}/field-references")
    @PreAuthorize("hasAuthority('standard.dictionary.view') and hasAuthority('model.view')")
    @Operation(summary = "查询引用码表的模型字段")
    public PageResponse<StandardDictionaryFieldReferenceResponse> fieldReferences(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.fieldReferences(id, request);
    }

    @GetMapping("/metadata-import-template")
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "下载码表 Excel 导入模板")
    public ResponseEntity<byte[]> metadataImportTemplate() {
        return excelFile(excelService.template());
    }

    @PostMapping("/actions/query-export-metadata")
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "导出选择的码表树元数据")
    public ResponseEntity<byte[]> exportMetadata(
            @Valid @RequestBody ExportStandardDictionaryMetadataRequest request
    ) {
        return excelFile(excelService.export(request));
    }

    @PostMapping(
            path = "/actions/query-import-preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "只读解析并预览码表 Excel")
    public StandardDictionaryImportPreviewResponse previewImport(
            @RequestPart("file") MultipartFile file
    ) {
        return excelService.preview(file);
    }

    @PostMapping(
            path = "/actions/import-metadata",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "提交已预览的码表 Excel")
    public StandardDictionaryImportResultResponse importMetadata(
            @RequestPart("file") MultipartFile file,
            @RequestParam String previewDigest
    ) {
        return excelService.importMetadata(file, previewDigest);
    }

    private ResponseEntity<byte[]> excelFile(StandardDictionaryExcelFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(excelService.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build());
        return new ResponseEntity<>(file.content(), headers, HttpStatus.OK);
    }
}
