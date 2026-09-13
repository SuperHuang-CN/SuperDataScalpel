package cn.superhuang.data.scalpel.business.standard.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
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
import io.swagger.v3.oas.annotations.Parameter;
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

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "分页查询码表")
    @GetMapping
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "分页查询码表", description = "分页查询码表基本信息、版本和启停状态，不展开码表节点树。")
    public PageResponse<StandardDictionaryResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询码表详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "查询码表详情", description = "读取码表详情及当前版本信息；码表项通过树接口单独查询。")
    public StandardDictionaryDetailResponse get(@Parameter(description = "码表 UUID") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增码表")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "新增码表", description = "创建空码表并建立初始版本信息，码表节点需通过节点接口继续维护。")
    public StandardDictionaryDetailResponse create(
            @Valid @RequestBody CreateStandardDictionaryRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改码表")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "修改码表", description = "按内容版本整体更新编码、名称、取值类型和说明；实际内容不变时不递增版本。已有节点时不能修改取值类型；被模型字段或模板字段引用后还不能修改编码，版本不一致时返回 409。")
    public StandardDictionaryDetailResponse update(
            @Parameter(description = "码表 UUID") @PathVariable UUID id,
            @Valid @RequestBody UpdateStandardDictionaryRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "启用码表")
    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "启用码表", description = "按乐观版本检查将码表置为启用；不会自动启用其已停用节点。")
    public StandardDictionaryDetailResponse enable(
            @Parameter(description = "码表 UUID") @PathVariable UUID id,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        return service.enable(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用码表")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "停用码表", description = "按内容版本将码表置为停用；不会删除节点或既有字段绑定，但会阻止其他字段新绑定该码表，并使树中所有节点的 effectiveEnabled 为 false。重复停用不递增版本。")
    public StandardDictionaryDetailResponse disable(
            @Parameter(description = "码表 UUID") @PathVariable UUID id,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        return service.disable(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除未被字段引用的码表")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "删除未被字段引用的码表", description = "仅无真实模型字段和常用字段模板字段引用的码表可删除；校验内容版本后，在同一管理库事务中移除全部节点和码表。")
    public void delete(
            @Parameter(description = "码表 UUID") @PathVariable UUID id,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        service.delete(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询码表项树")
    @GetMapping("/{dictionaryId}/items/tree")
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "查询码表项树", description = "返回指定码表的完整节点树、层级顺序和节点状态。")
    public List<StandardDictionaryItemTreeResponse> tree(
            @Parameter(description = "码表 UUID") @PathVariable UUID dictionaryId
    ) {
        return service.tree(dictionaryId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增码表树节点")
    @PostMapping("/{dictionaryId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "新增码表树节点", description = "在指定码表和父节点下新增节点，校验内容版本、整表 code 唯一性及对所有已绑定字段的安全可表达性。允许在停用码表或停用父节点下创建，但此时节点的实际可用状态为 false；成功后压实同级顺序并将码表版本递增 1。")
    public StandardDictionaryItemMutationResponse createItem(
            @Parameter(description = "码表 UUID") @PathVariable UUID dictionaryId,
            @Valid @RequestBody CreateStandardDictionaryItemRequest request
    ) {
        return service.createItem(dictionaryId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改码表树节点")
    @PostMapping("/{dictionaryId}/items/{itemId}/actions/update")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "修改码表树节点", description = "按内容版本整体更新节点 code、名称和说明，不改变父节点、顺序或启停状态。code 变化需满足整表唯一且可由全部已绑定字段安全表达；码表一旦被任一模型字段或模板字段引用，所有节点 code 都禁止修改。实际无变化时不递增版本。")
    public StandardDictionaryItemMutationResponse updateItem(
            @Parameter(description = "码表 UUID") @PathVariable UUID dictionaryId,
            @Parameter(description = "码表节点 UUID") @PathVariable UUID itemId,
            @Valid @RequestBody UpdateStandardDictionaryItemRequest request
    ) {
        return service.updateItem(dictionaryId, itemId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "移动码表树节点")
    @PostMapping("/{dictionaryId}/items/{itemId}/actions/move")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "移动码表树节点", description = "调整码表节点的父节点和同级顺序，阻止移动到自身后代，并返回更新后的版本。")
    public StandardDictionaryItemMutationResponse moveItem(
            @Parameter(description = "码表 UUID") @PathVariable UUID dictionaryId,
            @Parameter(description = "码表节点 UUID") @PathVariable UUID itemId,
            @Valid @RequestBody MoveStandardDictionaryItemRequest request
    ) {
        return service.moveItem(dictionaryId, itemId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "启用码表树节点")
    @PostMapping("/{dictionaryId}/items/{itemId}/actions/enable")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "启用码表树节点", description = "按码表版本检查启用指定节点；不会递归改变子节点状态。")
    public StandardDictionaryItemMutationResponse enableItem(
            @Parameter(description = "码表 UUID") @PathVariable UUID dictionaryId,
            @Parameter(description = "码表节点 UUID") @PathVariable UUID itemId,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        return service.enableItem(dictionaryId, itemId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用码表树节点")
    @PostMapping("/{dictionaryId}/items/{itemId}/actions/disable")
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "停用码表树节点", description = "按码表版本检查停用指定节点；不会删除节点或解除已有字段引用。")
    public StandardDictionaryItemMutationResponse disableItem(
            @Parameter(description = "码表 UUID") @PathVariable UUID dictionaryId,
            @Parameter(description = "码表节点 UUID") @PathVariable UUID itemId,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        return service.disableItem(dictionaryId, itemId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除码表树节点")
    @PostMapping("/{dictionaryId}/items/{itemId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "删除码表树节点", description = "校验内容版本后删除叶子节点并压实原同级顺序。只要整张码表被任一真实模型字段或常用字段模板字段引用，就禁止删除其中任何节点；有子节点时也禁止删除，不执行级联。")
    public void deleteItem(
            @Parameter(description = "码表 UUID") @PathVariable UUID dictionaryId,
            @Parameter(description = "码表节点 UUID") @PathVariable UUID itemId,
            @Valid @RequestBody StandardDictionaryVersionRequest request
    ) {
        service.deleteItem(dictionaryId, itemId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询引用码表的模型字段")
    @GetMapping("/{id}/field-references")
    @PreAuthorize("hasAuthority('standard.dictionary.view') and hasAuthority('model.view')")
    @Operation(summary = "查询引用码表的模型字段", description = "分页查询 standardDictionaryId 直接等于该码表 UUID 的真实模型字段，用于影响分析；不返回常用字段模板引用，模板引用数量只能从码表详情的 templateFieldReferenceCount 获取。")
    public PageResponse<StandardDictionaryFieldReferenceResponse> fieldReferences(
            @Parameter(description = "码表 UUID") @PathVariable UUID id,
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.fieldReferences(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "下载码表 Excel 导入模板")
    @GetMapping("/metadata-import-template")
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "下载码表 Excel 导入模板", description = "下载码表批量导入 Excel 模板；该二进制响应不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> metadataImportTemplate() {
        return excelFile(excelService.template());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "导出选择的码表树元数据")
    @PostMapping("/actions/query-export-metadata")
    @PreAuthorize("hasAuthority('standard.dictionary.view')")
    @Operation(summary = "导出选择的码表树元数据", description = "将请求选中的码表及节点树导出为 Excel，不改变码表；该二进制响应不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> exportMetadata(
            @Valid @RequestBody ExportStandardDictionaryMetadataRequest request
    ) {
        return excelFile(excelService.export(request));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "只读解析并预览码表 Excel")
    @PostMapping(
            path = "/actions/query-import-preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "只读解析并预览码表 Excel", description = "解析不超过 10 MiB 的 .xlsx 模板，最多读取 200 张码表和 20000 个节点；校验整个工作簿并返回规范化内容、预计动作和提交摘要，不保存码表或节点。任一问题都会阻止整批提交。")
    public StandardDictionaryImportPreviewResponse previewImport(
            @Parameter(description = "按码表模板填写的 Excel 工作簿") @RequestPart("file") MultipartFile file
    ) {
        return excelService.preview(file);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "提交已预览的码表 Excel")
    @PostMapping(
            path = "/actions/import-metadata",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAuthority('standard.dictionary.manage')")
    @Operation(summary = "提交已预览的码表 Excel", description = "重新解析同一 Excel，在锁定涉及的现有码表后比较 previewDigest；文件规范化内容、匹配结果、相关校验问题或码表内容版本变化时返回 409。码表和节点都按规范化 code 匹配，Excel 中改 code 会创建新对象并保留旧对象，不执行重命名。通过后在一个管理库事务中创建或更新文件声明内容；文件中未声明的现有对象不会删除。文件上传不属于系统 MCP 第一版支持范围。")
    public StandardDictionaryImportResultResponse importMetadata(
            @Parameter(description = "与预览时相同的 Excel 工作簿") @RequestPart("file") MultipartFile file,
            @Parameter(description = "预览响应中的 previewDigest；文件或现有码表状态变化时提交冲突") @RequestParam String previewDigest
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
