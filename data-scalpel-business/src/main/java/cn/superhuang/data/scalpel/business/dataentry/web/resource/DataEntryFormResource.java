package cn.superhuang.data.scalpel.business.dataentry.web.resource;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryFormStatus;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryImportFormat;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryDataService;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryFormService;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryImportFile;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryImportService;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryImportTemplateService;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryOperationLogService;
import cn.superhuang.data.scalpel.business.dataentry.web.request.CreateDataEntryFormRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.CreateDataEntryRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.DataEntryOptionQueryRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.DeleteDataEntryBatchRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.UpdateDataEntryLookupsRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryFormDetailResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryFormResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryHealthResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryImportPreviewResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryModelCandidateResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryMutationResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryOperationLogResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryOptionResponse;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDataQueryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
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

import java.security.Principal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-entry-forms")
@Tag(name = "数据填报")
public class DataEntryFormResource {

    private final DataEntryFormService formService;
    private final DataEntryDataService dataService;
    private final DataEntryOperationLogService logService;
    private final DataEntryImportTemplateService importTemplateService;
    private final DataEntryImportService importService;

    public DataEntryFormResource(
            DataEntryFormService formService,
            DataEntryDataService dataService,
            DataEntryOperationLogService logService,
            DataEntryImportTemplateService importTemplateService,
            DataEntryImportService importService
    ) {
        this.formService = formService;
        this.dataService = dataService;
        this.logService = logService;
        this.importTemplateService = importTemplateService;
        this.importService = importService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "查询填报表单")
    public PageResponse<DataEntryFormResponse> search(
            @RequestParam(required = false) DataEntryFormStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return formService.search(status, keyword, page, size);
    }

    @GetMapping("/model-candidates")
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "查询尚未建立填报表单的模型")
    public List<DataEntryModelCandidateResponse> candidates(@RequestParam(required = false) String keyword) {
        return formService.candidates(keyword);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "查询填报表单详情")
    public DataEntryFormDetailResponse get(@PathVariable UUID id) {
        return formService.get(id);
    }

    @GetMapping("/{id}/health")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "实时检查填报表单健康状态")
    public DataEntryHealthResponse health(@PathVariable UUID id) {
        return formService.health(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "为模型创建填报表单")
    public DataEntryFormDetailResponse create(@Valid @RequestBody CreateDataEntryFormRequest request) {
        return formService.create(request);
    }

    @PostMapping("/{id}/actions/update-lookups")
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "整体替换关联模型下拉配置")
    public DataEntryFormDetailResponse updateLookups(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDataEntryLookupsRequest request
    ) {
        return formService.updateLookups(id, request);
    }

    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "发布填报表单")
    public DataEntryFormDetailResponse publish(@PathVariable UUID id) {
        return formService.publish(id);
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "停用填报表单")
    public DataEntryFormDetailResponse disable(@PathVariable UUID id) {
        return formService.disable(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "删除填报表单及其配置和操作日志")
    public void delete(@PathVariable UUID id) {
        formService.delete(id);
    }

    /** This POST endpoint is read-only; the request body carries the existing structured model query contract. */
    @PostMapping("/{id}/actions/query-data")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "按条件查询填报目标物理表数据")
    public DataModelDataQueryResponse queryData(
            @PathVariable UUID id,
            @Valid @RequestBody DataModelDataQueryRequest request
    ) {
        return dataService.queryData(id, request);
    }

    /** This POST endpoint is read-only; it supports structured paging and historical-value lookup. */
    @PostMapping("/{id}/fields/{fieldId}/actions/query-options")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "搜索字段下拉选项或反查已有值")
    public DataEntryOptionResponse queryOptions(
            @PathVariable UUID id,
            @PathVariable UUID fieldId,
            @Valid @RequestBody DataEntryOptionQueryRequest request
    ) {
        return dataService.queryOptions(id, fieldId, request);
    }

    @PostMapping("/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('dataentry.submit')")
    @Operation(summary = "新增一条填报数据")
    public DataEntryMutationResponse insert(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDataEntryRequest request,
            Principal principal
    ) {
        return dataService.insert(id, request, principal == null ? null : principal.getName());
    }

    @GetMapping("/{id}/entries/import-template")
    @PreAuthorize("hasAuthority('dataentry.submit')")
    @Operation(summary = "下载当前模型的 Excel 或 CSV 填报模板")
    public ResponseEntity<byte[]> importTemplate(
            @PathVariable UUID id,
            @RequestParam DataEntryImportFormat format
    ) {
        DataEntryImportFile file = importTemplateService.template(id, format);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build());
        return new ResponseEntity<>(file.content(), headers, HttpStatus.OK);
    }

    /** This POST endpoint is read-only; it parses and validates the uploaded file without changing either database. */
    @PostMapping(
            path = "/{id}/entries/actions/preview-import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('dataentry.submit')")
    @Operation(summary = "只读校验并预览 Excel/CSV 批量填报文件")
    public DataEntryImportPreviewResponse previewImport(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file
    ) {
        return importService.preview(id, file);
    }

    @PostMapping(
            path = "/{id}/entries/actions/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('dataentry.submit')")
    @Operation(summary = "导入已预览的 Excel/CSV 填报数据")
    public DataEntryMutationResponse importEntries(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam String previewDigest,
            Principal principal
    ) {
        return importService.importData(id, file, previewDigest, principal == null ? null : principal.getName());
    }

    @PostMapping("/{id}/entries/actions/delete-batch")
    @PreAuthorize("hasAuthority('dataentry.delete')")
    @Operation(summary = "按单字段或联合业务主键批量删除目标数据")
    public DataEntryMutationResponse deleteBatch(
            @PathVariable UUID id,
            @Valid @RequestBody DeleteDataEntryBatchRequest request,
            Principal principal
    ) {
        return dataService.deleteBatch(id, request, principal == null ? null : principal.getName());
    }

    @GetMapping("/{id}/operation-logs")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "查询填报数据操作日志")
    public PageResponse<DataEntryOperationLogResponse> operationLogs(
            @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        formService.requireForm(id);
        return logService.search(id, request);
    }

    @GetMapping("/{id}/operation-logs/{logId}")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "查询填报数据操作日志详情")
    public DataEntryOperationLogResponse operationLog(@PathVariable UUID id, @PathVariable UUID logId) {
        formService.requireForm(id);
        return logService.get(id, logId);
    }
}
