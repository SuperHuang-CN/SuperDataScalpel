package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "指标工作簿解析后的逐行变更计划和导入可执行性。")

public record MetricImportPreviewResponse(
        @Schema(description = "规范化内容指纹，用于并发检查和防止预览后内容变化。")
        String fingerprint,
        @Schema(description = "输入文件总数据行数。")
        int totalRows,
        @Schema(description = "预览判定为将新建指标的行数。")
        int createCount,
        @Schema(description = "预览判定为将更新现有指标基础资料或草稿的行数。")
        int updateCount,
        @Schema(description = "内容与当前系统一致、无需写入的指标行数。")
        int unchangedCount,
        @Schema(description = "阻断性错误数量。")
        int errorCount,
        @Schema(description = "非阻断告警数量。")
        int warningCount,
        @Schema(description = "当前预览是否没有阻断性问题并可执行导入。")
        boolean canImport,
        @Schema(description = "按 Excel 原始顺序返回的逐行动作、差异和问题。")
        List<MetricImportRowResponse> rows
) {}
