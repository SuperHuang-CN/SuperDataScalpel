package cn.superhuang.data.scalpel.business.metric.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.metric.service.MetricExcelCodec;
import cn.superhuang.data.scalpel.business.metric.service.MetricExcelFile;
import cn.superhuang.data.scalpel.business.metric.service.MetricExcelService;
import cn.superhuang.data.scalpel.business.metric.web.request.ExportMetricsRequest;
import cn.superhuang.data.scalpel.business.metric.web.response.MetricImportPreviewResponse;
import cn.superhuang.data.scalpel.business.metric.web.response.MetricImportResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@io.swagger.v3.oas.annotations.tags.Tag(name = "指标 Excel")
@RestController
@RequestMapping("/api/v1/metrics/actions")
public class MetricExcelResource {
    private final MetricExcelService excel;
    public MetricExcelResource(MetricExcelService excel) { this.excel = excel; }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "指标 Excel：下载模板")
    @Operation(summary = "下载指标导入模板", description = "下载包含字段说明、枚举选项和示例的 XLSX 空白模板。该文件下载接口不属于系统 MCP 第一版支持范围。")
    @GetMapping("/download-import-template")
    @PreAuthorize("hasAuthority('metric.manage')")
    public ResponseEntity<byte[]> template() { return file(excel.template()); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "只读：按筛选条件与勾选范围导出指标口径，不改变状态")
    @PostMapping("/query-export")
    @PreAuthorize("hasAuthority('metric.view')")
    @Operation(summary = "导出指标口径", description = "按 Search DSL 或勾选 ID 导出最多 1000 条指标的草稿或已发布口径。导出草稿需要维护或发布权限；不会修改指标。该文件接口不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportMetricsRequest request) { return file(excel.export(request)); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "只读：校验并预览 Excel 导入，不保存任何指标或预览记录")
    @PostMapping(value = "/query-import-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('metric.manage')")
    @Operation(summary = "预览指标 Excel 导入", description = "解析并校验 XLSX，按行返回新增、更新、不变或错误计划以及内容指纹；不保存指标或暂存预览。该文件接口不属于系统 MCP 第一版支持范围。")
    public MetricImportPreviewResponse preview(@Parameter(description = "上传文件；格式、大小和内容由当前接口校验。") @RequestPart("file") MultipartFile file) { return excel.preview(file); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "只读：重新校验 Excel 并下载错误清单，不改变状态")
    @PostMapping(value = "/query-import-errors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('metric.manage')")
    @Operation(summary = "下载指标导入错误清单", description = "重新解析上传的 XLSX，并下载带行号、字段和阻断/警告说明的错误清单；不使用客户端传回的诊断。该文件接口不属于系统 MCP 第一版支持范围。")
    public ResponseEntity<byte[]> errors(@Parameter(description = "上传文件；格式、大小和内容由当前接口校验。") @RequestPart("file") MultipartFile file) { return file(excel.errors(file)); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "指标 Excel：导入文件")
    @Operation(summary = "导入指标 Excel", description = "重新解析文件并核对预览指纹，在一个事务中创建或更新指标基础资料和草稿口径；文件、目录或现有草稿变化以及任一阻断错误都会使整批不导入。该文件接口不属于系统 MCP 第一版支持范围。")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('metric.manage')")
    public MetricImportResultResponse importFile(@Parameter(description = "上传文件；格式、大小和内容由当前接口校验。") @RequestPart("file") MultipartFile file, @Parameter(description = "客户端预览时取得的内容指纹；不一致时拒绝执行导入。") @RequestParam("expectedPreviewFingerprint") String expectedPreviewFingerprint) {
        return excel.importFile(file, expectedPreviewFingerprint);
    }

    private static ResponseEntity<byte[]> file(MetricExcelFile file) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(MetricExcelCodec.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }
}
