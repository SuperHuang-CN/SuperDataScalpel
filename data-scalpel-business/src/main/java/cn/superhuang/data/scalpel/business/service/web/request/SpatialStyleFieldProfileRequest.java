package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "只读分析空间服务模型字段的唯一值频次或数值分级断点。")

public record SpatialStyleFieldProfileRequest(
        @Schema(description = "模型字段稳定编码。")
        @NotBlank String fieldCode,
        @Schema(description = "分析类型：UNIQUE_VALUES 返回唯一值及频次，CLASS_BREAKS 为数值字段计算分级断点。")
        @NotNull ProfileType profileType,
        @Schema(description = "UNIQUE_VALUES 最多返回的唯一值数量，范围 1～50，省略时为 12；CLASS_BREAKS 模式忽略该字段。")
        @Min(1) @Max(50) Integer limit,
        @Schema(description = "CLASS_BREAKS 使用的数值分级方法，省略时为 EQUAL_INTERVAL；UNIQUE_VALUES 模式忽略该字段。")
        ClassificationMethod classificationMethod,
        @Schema(description = "CLASS_BREAKS 期望生成的分级数量，范围 3～9，省略时为 5；UNIQUE_VALUES 模式忽略该字段，实际分级数可能因空值、常量或重复分位点而减少。")
        @Min(3) @Max(9) Integer classCount
) {
    @Schema(description = "字段分析方式：UNIQUE_VALUES 统计唯一值及频次，CLASS_BREAKS 计算数值分级断点")
    public enum ProfileType {
        UNIQUE_VALUES,
        CLASS_BREAKS
    }

    @Schema(description = "数值分级方法：EQUAL_INTERVAL 等距分级，QUANTILE 分位数分级")

    public enum ClassificationMethod {
        EQUAL_INTERVAL,
        QUANTILE
    }
}
