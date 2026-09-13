package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "指标工作簿中一行对应的预计导入动作、字段差异和校验问题。")

public record MetricImportRowResponse(
        @Schema(description = "Excel 中的原始数据行号，从 1 开始。")
        int rowNumber,
        @Schema(description = "该行解析出的指标稳定技术编码。")
        String code,
        @Schema(description = "该行解析出的指标显示名称。")
        String name,
        @Schema(description = "预览动作：CREATE 新建、UPDATE 更新或 UNCHANGED 不变。")
        String action,
        @Schema(description = "该行将产生的字段变更；新建或未变化时可为空列表。")
        List<MetricImportChangeResponse> changes,
        @Schema(description = "该行的阻断错误和非阻断告警。")
        List<MetricImportIssueResponse> issues
) {}
