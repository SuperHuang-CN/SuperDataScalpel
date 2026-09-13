package cn.superhuang.data.scalpel.business.dataentry.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryFormStatus;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryImportFormat;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryDataService;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryFormService;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryImportFile;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryImportService;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryImportTemplateService;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryOperationLogService;
import cn.superhuang.data.scalpel.business.dataentry.service.DataEntryRecordChangeService;
import cn.superhuang.data.scalpel.business.dataentry.web.request.CreateDataEntryFormRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.CreateDataEntryRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.DataEntryOptionQueryRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.DataEntryRecordKeyRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.DeleteDataEntryBatchRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.UpdateDataEntryLookupsRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.UpdateDataEntryRecordRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryFormDetailResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryFormResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryHealthResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryImportPreviewResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryModelCandidateResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryMutationResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryOperationLogResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryOptionResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryRecordChangeResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryRecordDetailResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryUpdateResponse;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDataQueryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final DataEntryRecordChangeService changeService;
    private final DataEntryImportTemplateService importTemplateService;
    private final DataEntryImportService importService;

    public DataEntryFormResource(
            DataEntryFormService formService,
            DataEntryDataService dataService,
            DataEntryOperationLogService logService,
            DataEntryRecordChangeService changeService,
            DataEntryImportTemplateService importTemplateService,
            DataEntryImportService importService
    ) {
        this.formService = formService;
        this.dataService = dataService;
        this.logService = logService;
        this.changeService = changeService;
        this.importTemplateService = importTemplateService;
        this.importService = importService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询填报表单",
            keywords = {"数据填报", "表单", "模型", "健康状态"},
            relatedOperations = {"GET /api/v1/data-entry-forms/{id}", "GET /api/v1/data-entry-forms/{id}/health"})
    @GetMapping
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "查询填报表单", description = "按状态和名称/编码分页查询表单，只做管理库元数据诊断，不连接目标数据库；详情中的实时健康状态可能与列表诊断不同。")
    public PageResponse<DataEntryFormResponse> search(
            @Parameter(description = "可选状态筛选；为空时不限制状态。") @RequestParam(required = false) DataEntryFormStatus status,
            @Parameter(description = "可选关键词，用于名称或编码的模糊匹配。") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码，从 0 开始；省略时为 0。") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页数量；省略时使用服务端默认值。") @RequestParam(required = false) Integer size
    ) {
        return formService.search(status, keyword, page, size);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询尚未建立填报表单的模型",
            keywords = {"数据填报", "候选模型", "创建表单"},
            relatedOperations = {"POST /api/v1/data-entry-forms"})
    @GetMapping("/model-candidates")
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "查询尚未建立填报表单的模型", description = "返回当前尚未绑定表单的模型候选及基础状态。创建时仍会重新校验模型存在且未被其他表单绑定。")
    public List<DataEntryModelCandidateResponse> candidates(@Parameter(description = "可选关键词，用于名称或编码的模糊匹配。") @RequestParam(required = false) String keyword) {
        return formService.candidates(keyword);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询填报表单详情",
            keywords = {"数据填报", "表单详情", "字段", "下拉配置", "健康检查"},
            relatedOperations = {"GET /api/v1/data-entry-forms/{id}/health", "POST /api/v1/data-entry-forms/{id}/actions/query-data"})
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "查询填报表单详情", description = "返回目标模型字段、业务主键、码表和关联模型下拉配置，并执行完整实时健康检查；可能连接目标及关联来源数据库，但不会修改数据。")
    public DataEntryFormDetailResponse get(@Parameter(description = "填报表单 UUID。") @PathVariable UUID id) {
        return formService.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "实时检查填报表单健康状态",
            keywords = {"数据填报", "健康检查", "可发布", "可提交", "可编辑", "可删除", "可查询"})
    @GetMapping("/{id}/health")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "实时检查填报表单健康状态", description = "检查模型生命周期与结构版本、数据库能力、物理表、业务主键、码表和关联下拉，分别给出 canPublish、canSubmit、canUpdateEntries、canDeleteEntries、canQueryEntries 及稳定问题码。只读但会访问外部数据库。")
    public DataEntryHealthResponse health(@Parameter(description = "填报表单 UUID。") @PathVariable UUID id) {
        return formService.health(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "为模型创建填报表单",
            keywords = {"数据填报", "创建表单", "模型"},
            prerequisites = "目标模型存在且尚未建立填报表单。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "为模型创建填报表单", description = "为一个现有模型创建唯一 DRAFT 表单并返回 201；不会创建或修改模型、物理表和业务数据。")
    public DataEntryFormDetailResponse create(@Valid @RequestBody CreateDataEntryFormRequest request) {
        return formService.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "整体替换关联模型下拉配置",
            keywords = {"数据填报", "关联模型", "下拉选项", "字段配置"},
            prerequisites = "表单为 DRAFT 或 DISABLED；目标字段和来源模型/标签字段满足关联下拉约束。")
    @PostMapping("/{id}/actions/update-lookups")
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "整体替换关联模型下拉配置", description = "以请求中的完整列表替换现有 LOOKUP 配置；空列表清除全部关联下拉。来源 value 固定使用来源模型唯一的单字段业务主键。")
    public DataEntryFormDetailResponse updateLookups(
            @Parameter(description = "填报表单 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateDataEntryLookupsRequest request
    ) {
        return formService.updateLookups(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "发布填报表单",
            keywords = {"数据填报", "发布表单"},
            prerequisites = "表单为 DRAFT 或 DISABLED，且完整实时健康检查的 canPublish 为 true。")
    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "发布填报表单", description = "执行完整实时健康检查后将表单设为 PUBLISHED，并记录本次确认的模型 schemaVersion；不创建表、修改模型或写入业务数据。")
    public DataEntryFormDetailResponse publish(@Parameter(description = "填报表单 UUID。") @PathVariable UUID id) {
        return formService.publish(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用填报表单",
            keywords = {"数据填报", "停用表单"}, prerequisites = "表单当前为 PUBLISHED。")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "停用填报表单", description = "将已发布表单设为 DISABLED，立即阻止新增、编辑、导入和删除；即使目标模型已失效也允许停用。不会修改目标物理表数据。")
    public DataEntryFormDetailResponse disable(@Parameter(description = "填报表单 UUID。") @PathVariable UUID id) {
        return formService.disable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除填报表单及其配置和操作日志",
            keywords = {"数据填报", "删除表单"}, prerequisites = "表单为 DRAFT 或 DISABLED；PUBLISHED 必须先停用。")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('dataentry.manage')")
    @Operation(summary = "删除填报表单及其配置和操作日志", description = "删除表单、关联下拉配置和管理库操作日志并返回 204；目标模型、物理表及其中数据始终保留。")
    public void delete(@Parameter(description = "填报表单 UUID。") @PathVariable UUID id) {
        formService.delete(id);
    }

    /** This POST endpoint is read-only; the request body carries the existing structured model query contract. */
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "按条件查询填报目标物理表数据",
            keywords = {"数据填报", "查询数据", "物理表", "筛选", "分页"},
            prerequisites = "实时健康检查的 canQueryEntries 为 true。")
    @PostMapping("/{id}/actions/query-data")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "按条件查询填报目标物理表数据", description = "复用模型结构化数据查询契约读取目标物理表。该 POST 仅为承载复杂查询条件，严格只读；DRAFT 或 DISABLED 表单只要目标表可访问也可用于排查。")
    public DataModelDataQueryResponse queryData(
            @Parameter(description = "填报表单 UUID。") @PathVariable UUID id,
            @Valid @RequestBody DataModelDataQueryRequest request
    ) {
        return dataService.queryData(id, request);
    }

    /** This POST endpoint is read-only; a structured composite key cannot be represented safely in a URL. */
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "按业务主键查询一条填报记录详情")
    @PostMapping("/{id}/entries/actions/query-detail")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "查询填报记录详情", description = "按当前模型完整业务主键重新读取目标表中的完整记录；该 POST 严格只读。")
    public DataEntryRecordDetailResponse queryDetail(@PathVariable UUID id,
            @Valid @RequestBody DataEntryRecordKeyRequest request) {
        return dataService.queryDetail(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "按业务主键编辑一条填报记录")
    @PostMapping("/{id}/entries/actions/update")
    @PreAuthorize("hasAuthority('dataentry.submit')")
    @Operation(summary = "编辑填报记录", description = "业务主键只读；目标记录必须准确命中一行。没有字段变化时不执行 UPDATE，也不产生记录级变更。")
    public DataEntryUpdateResponse updateEntry(@PathVariable UUID id,
            @Valid @RequestBody UpdateDataEntryRecordRequest request, Principal principal) {
        return dataService.update(id, request, principal == null ? null : principal.getName());
    }

    /** This POST endpoint is read-only; it supports structured paging and historical-value lookup. */
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "搜索字段下拉选项或反查已有值",
            keywords = {"数据填报", "字段选项", "码表", "关联模型", "历史值"})
    @PostMapping("/{id}/fields/{fieldId}/actions/query-options")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "搜索字段下拉选项或反查已有值", description = "对码表或关联模型字段分页搜索可选值，也可一次反查最多 100 个已有标量值。返回 ACTIVE、DISABLED、MISSING 或 SOURCE_UNAVAILABLE，不改变配置或数据。")
    public DataEntryOptionResponse queryOptions(
            @Parameter(description = "填报表单 UUID。") @PathVariable UUID id,
            @Parameter(description = "目标模型字段 UUID；该字段必须绑定码表或关联模型下拉。") @PathVariable UUID fieldId,
            @Valid @RequestBody DataEntryOptionQueryRequest request
    ) {
        return dataService.queryOptions(id, fieldId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增一条填报数据",
            keywords = {"数据填报", "新增记录", "提交"},
            prerequisites = "表单已发布且 canSubmit 为 true；请求恰好包含当前模型全部字段编码；业务主键不存在。")
    @PostMapping("/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('dataentry.submit')")
    @Operation(summary = "新增一条填报数据", description = "严格转换并校验全部字段、码表和关联来源后，对目标物理表执行参数化 INSERT；不执行 UPSERT。目标数据库与操作日志不构成分布式事务。")
    public DataEntryMutationResponse insert(
            @Parameter(description = "填报表单 UUID。") @PathVariable UUID id,
            @Valid @RequestBody CreateDataEntryRequest request,
            Principal principal
    ) {
        return dataService.insert(id, request, principal == null ? null : principal.getName());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载当前模型的 Excel 或 CSV 填报模板")
    @GetMapping("/{id}/entries/import-template")
    @PreAuthorize("hasAuthority('dataentry.submit')")
    @Operation(summary = "下载当前模型的 Excel 或 CSV 填报模板", description = "按当前模型字段生成 XLSX 或 UTF-8 CSV 两行表头模板；返回二进制文件，因此不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> importTemplate(
            @Parameter(description = "填报表单 UUID。") @PathVariable UUID id,
            @Parameter(description = "模板格式：XLSX 或 CSV。") @RequestParam DataEntryImportFormat format
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
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "只读校验并预览 Excel/CSV 批量填报文件")
    @PostMapping(
            path = "/{id}/entries/actions/preview-import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('dataentry.submit')")
    @Operation(summary = "只读校验并预览 Excel/CSV 批量填报文件", description = "扫描完整文件并校验模型结构、类型、业务主键、码表和关联值，不写目标数据库。响应最多含前 100 行和 200 个问题，并给出确认导入所需 previewDigest；该文件接口不属于系统 MCP 第一版支持范围。")
    public DataEntryImportPreviewResponse previewImport(
            @Parameter(description = "填报表单 UUID。") @PathVariable UUID id,
            @Parameter(description = "最大 50 MiB 的 .xlsx 或 UTF-8 .csv 文件，最多 100000 条非空数据行。") @RequestPart("file") MultipartFile file
    ) {
        return importService.preview(id, file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "导入已预览的 Excel/CSV 填报数据",
            prerequisites = "同一文件已通过 preview-import，previewDigest 与当前表单、模型和引用配置仍一致，且 canSubmit 为 true。")
    @PostMapping(
            path = "/{id}/entries/actions/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('dataentry.submit')")
    @Operation(summary = "导入已预览的 Excel/CSV 填报数据", description = "重新执行完整校验和摘要核对后，每 500 行参数化 INSERT 并立即生效，返回 201。中途失败不会回滚已提交批次；结果不确定时要求人工核对且不得自动重试。该文件接口不属于系统 MCP 第一版支持范围。")
    public DataEntryMutationResponse importEntries(
            @Parameter(description = "填报表单 UUID。") @PathVariable UUID id,
            @Parameter(description = "与预览阶段完全相同的 .xlsx 或 UTF-8 .csv 文件。") @RequestPart("file") MultipartFile file,
            @Parameter(description = "客户端预览时取得的摘要；不一致时拒绝执行导入。") @RequestParam String previewDigest,
            Principal principal
    ) {
        return importService.importData(id, file, previewDigest, principal == null ? null : principal.getName());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "按单字段或联合业务主键批量删除目标数据",
            keywords = {"数据填报", "批量删除", "业务主键"},
            prerequisites = "表单已发布且 canDeleteEntries 为 true；每个 key 完整、互不重复并恰好命中一行。")
    @PostMapping("/{id}/entries/actions/delete-batch")
    @PreAuthorize("hasAuthority('dataentry.delete')")
    @Operation(summary = "按单字段或联合业务主键批量删除目标数据", description = "一次按最多 100 组完整业务主键删除目标物理表记录。删除不承诺跨数据库回滚；部分完成或结果不确定时返回人工核对提示，不能直接重试。")
    public DataEntryMutationResponse deleteBatch(
            @Parameter(description = "填报表单 UUID。") @PathVariable UUID id,
            @Valid @RequestBody DeleteDataEntryBatchRequest request,
            Principal principal
    ) {
        return dataService.deleteBatch(id, request, principal == null ? null : principal.getName());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询填报数据操作日志",
            keywords = {"数据填报", "操作日志", "新增", "编辑", "导入", "删除"})
    @GetMapping("/{id}/operation-logs")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "查询填报数据操作日志", description = "使用通用 Search DSL 查询该表单的 INSERT、UPDATE、IMPORT 和 DELETE 操作日志。日志保存操作汇总；完整逐记录快照通过记录级变更接口分页读取。")
    public PageResponse<DataEntryOperationLogResponse> operationLogs(
            @Parameter(description = "填报表单 UUID。") @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        formService.requireForm(id);
        return logService.search(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询填报数据操作日志详情",
            keywords = {"数据填报", "操作日志详情", "人工核对"})
    @GetMapping("/{id}/operation-logs/{logId}")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @Operation(summary = "查询填报数据操作日志详情", description = "返回指定表单下的一条操作日志，包括处理状态、请求/确认数量、安全摘要和是否需要人工核对。")
    public DataEntryOperationLogResponse operationLog(@Parameter(description = "填报表单 UUID。") @PathVariable UUID id, @Parameter(description = "操作日志 UUID；必须属于该表单。") @PathVariable UUID logId) {
        formService.requireForm(id);
        return logService.get(id, logId);
    }

    @GetMapping("/{id}/record-changes")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询记录级变更历史",
            keywords = {"数据填报", "记录历史", "字段变化", "操作日志"})
    @Operation(summary = "查询记录级变更历史", description = "可按稳定记录标识或操作日志 UUID 筛选，历史查询不访问目标数据库。")
    public PageResponse<DataEntryRecordChangeResponse> recordChanges(@PathVariable UUID id,
            @RequestParam(required = false) String recordKey,
            @RequestParam(required = false) UUID operationLogId,
            @ParameterObject @ModelAttribute SearchRequest request) {
        formService.requireForm(id);
        return changeService.search(id, recordKey, operationLogId, request);
    }

    @GetMapping("/{id}/record-changes/{changeId}")
    @PreAuthorize("hasAuthority('dataentry.view')")
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询一次记录变更详情",
            keywords = {"数据填报", "记录变更详情", "前后值"})
    @Operation(summary = "查询一次记录变更详情")
    public DataEntryRecordChangeResponse recordChange(@PathVariable UUID id, @PathVariable UUID changeId) {
        formService.requireForm(id);
        return changeService.get(id, changeId);
    }
}
