package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** In size mode, the existing binSize/binSizeUnit supply the requested approximate diameter. */
@JsonClassDescription("H3 空间分箱参数；直接选择 0 至 15 的原生分辨率，或按期望平均对边距离选择最接近的分辨率。H3 单元实际尺寸随位置变化，近似选级不声明与 ArcGIS 使用相同公式。")
public record SpatialH3Options(
        @JsonPropertyDescription("必填的 H3 大小模式：RESOLUTION 只使用 resolution；APPROXIMATE_SIZE 只使用外层 binSize/binSizeUnit，并按平均对边距离绝对差选择级别，平局选择较粗级别。")
        Mode mode,
        @JsonPropertyDescription("RESOLUTION 模式必填的整数级别，范围 0 至 15；APPROXIMATE_SIZE 模式忽略并可作为草稿保留。")
        Integer resolution
) {
    @JsonClassDescription("H3 大小选择方式：RESOLUTION 使用明确层级；APPROXIMATE_SIZE 根据外层期望平均对边距离选择最近层级。")
    public enum Mode { RESOLUTION, APPROXIMATE_SIZE }
}
