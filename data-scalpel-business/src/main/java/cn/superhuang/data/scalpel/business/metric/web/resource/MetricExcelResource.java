package cn.superhuang.data.scalpel.business.metric.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.metric.service.MetricExcelCodec;
import cn.superhuang.data.scalpel.business.metric.service.MetricExcelFile;
import cn.superhuang.data.scalpel.business.metric.service.MetricExcelService;
import cn.superhuang.data.scalpel.business.metric.web.request.ExportMetricsRequest;
import cn.superhuang.data.scalpel.business.metric.web.response.MetricImportPreviewResponse;
import cn.superhuang.data.scalpel.business.metric.web.response.MetricImportResultResponse;
import io.swagger.v3.oas.annotations.Operation;
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
    @GetMapping("/download-import-template")
    @PreAuthorize("hasAuthority('metric.manage')")
    public ResponseEntity<byte[]> template() { return file(excel.template()); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "只读：按筛选条件与勾选范围导出指标口径，不改变状态")
    @PostMapping("/query-export")
    @PreAuthorize("hasAuthority('metric.view')")
    @Operation(summary = "只读：按筛选条件与勾选范围导出指标口径，不改变状态")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ExportMetricsRequest request) { return file(excel.export(request)); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "只读：校验并预览 Excel 导入，不保存任何指标或预览记录")
    @PostMapping(value = "/query-import-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('metric.manage')")
    @Operation(summary = "只读：校验并预览 Excel 导入，不保存任何指标或预览记录")
    public MetricImportPreviewResponse preview(@RequestPart("file") MultipartFile file) { return excel.preview(file); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "只读：重新校验 Excel 并下载错误清单，不改变状态")
    @PostMapping(value = "/query-import-errors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('metric.manage')")
    @Operation(summary = "只读：重新校验 Excel 并下载错误清单，不改变状态")
    public ResponseEntity<byte[]> errors(@RequestPart("file") MultipartFile file) { return file(excel.errors(file)); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "指标 Excel：导入文件")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('metric.manage')")
    public MetricImportResultResponse importFile(@RequestPart("file") MultipartFile file, @RequestParam("expectedPreviewFingerprint") String expectedPreviewFingerprint) {
        return excel.importFile(file, expectedPreviewFingerprint);
    }

    private static ResponseEntity<byte[]> file(MetricExcelFile file) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(MetricExcelCodec.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }
}
