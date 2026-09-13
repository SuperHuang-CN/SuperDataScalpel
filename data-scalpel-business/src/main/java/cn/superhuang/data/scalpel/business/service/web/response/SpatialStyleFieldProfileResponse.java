package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;

import java.util.List;

@Schema(description = "对一个样式字段执行的唯一值或数值分级统计，供生成分类渲染规则使用。")

public record SpatialStyleFieldProfileResponse(
        @Schema(description = "本次统计对应的模型字段及样式能力。")
        SpatialStyleFieldResponse field,
        @Schema(description = "来源物理表总行数，等于 nonNullCount 与 nullCount 之和。")
        long totalRowCount,
        @Schema(description = "来源物理表中该字段非 NULL 的行数。")
        long nonNullCount,
        @Schema(description = "来源物理表中该字段为 NULL 的行数。")
        long nullCount,
        @Schema(description = "UNIQUE_VALUES 结果，按出现次数倒序、值升序返回；CLASS_BREAKS 或没有合格非空值时为空列表。超过 512 字符的值会省略。")
        List<UniqueValue> uniqueValues,
        @Schema(description = "UNIQUE_VALUES 是否仍有更多唯一值，或是否省略了超过 512 字符的值；CLASS_BREAKS 固定为 false。")
        boolean truncated,
        @Schema(description = "数值分析得到的最小值，以规范十进制字符串返回；唯一值分析时为空。")
        String minimum,
        @Schema(description = "数值分析得到的最大值，以规范十进制字符串返回；唯一值分析时为空。")
        String maximum,
        @Schema(description = "数值分级的有序断点，以规范十进制字符串返回；唯一值分析时为空列表。")
        List<String> breaks,
        @Schema(description = "UNIQUE_VALUES 返回的唯一值数量，或 CLASS_BREAKS 根据数据分布实际生成的分级数；可能小于请求值，没有可分级数据时为 0。")
        int actualClassCount,
        @Schema(description = "统计截断、数据不足或降级处理等非阻断告警；没有时为空列表。")
        List<String> warnings
) {
    @Schema(description = "一个类型化唯一值及其在当前服务来源数据中的出现次数")
    public record UniqueValue(
            @Schema(description = "值类型：STRING、NUMBER 或 BOOLEAN，决定 value 的解析方式。")
            SpatialStyleDocument.ValueType valueType,
            @Schema(description = "按字段类型规范化后的唯一值字符串。")
            String value,
            @Schema(description = "该值在来源物理表中的出现行数。")
            long count
    ) {
    }
}
