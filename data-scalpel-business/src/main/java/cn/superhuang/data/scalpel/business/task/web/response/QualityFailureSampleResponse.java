package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "单条质量规则的失败样本；样本受数量上限约束，不代表全部违规数据。")

public record QualityFailureSampleResponse(
        @Schema(description = "质量规则 UUID。")
        UUID ruleId,
        @Schema(description = "质量规则名称。")
        String ruleName,
        @Schema(description = "本次制品实际保存并返回的失败样本行数，范围 1 到 1000。")
        long sampledRows,
        @Schema(description = "该规则在本次运行中检查出的违规总行数，始终大于等于 sampledRows。")
        long violationRows,
        @Schema(description = "违规数据是否超过采样上限；true 表示 rows 只包含部分违规记录。")
        boolean truncated,
        @Schema(description = "样本是否包含足以稳定定位原始记录的主键列；false 时 rows 仍可用于观察失败值，但不能据此安全定位或修改源记录。")
        boolean rowLocatable,
        @Schema(description = "本次质量结果制品声明并经 Parquet 字段顺序校验的样本列定义。")
        List<Column> columns,
        @Schema(description = "失败样本数据，键按 columns.code 顺序返回。LONG 和 DECIMAL 转为十进制字符串以避免 JSON 精度损失，DATE/TIMESTAMP/TIMESTAMP_NTZ 转为标准字符串，NULL 保持 null；行数等于 sampledRows。")
        List<Map<String, Object>> rows
) {
    public QualityFailureSampleResponse {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    @Schema(description = "质量失败样本中的列定义。")

    public record Column(
            @Schema(description = "模型字段 UUID；诊断生成列可能为空。")
            UUID fieldId,
            @Schema(description = "样本列稳定编码，也是 rows 中对应值的键。")
            String code,
            @Schema(description = "样本列显示名称。")
            String name,
            @Schema(description = "字段的平台类型定义。")
            PlatformTypeDefinition type,
            @Schema(description = "是否为用于定位样本的主键字段。")
            boolean primaryKey,
            @Schema(description = "是否为平台附加的诊断列，而非目标模型业务字段。")
            boolean diagnostic
    ) {
    }
}
