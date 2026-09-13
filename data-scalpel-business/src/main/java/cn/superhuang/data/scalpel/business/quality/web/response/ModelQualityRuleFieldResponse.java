package cn.superhuang.data.scalpel.business.quality.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.UUID;

@Schema(description = "规则当前引用的模型字段摘要，用于把定义中的字段 UUID 解释为可读字段。")
public record ModelQualityRuleFieldResponse(
        @Schema(description = "模型字段 UUID，与规则定义中的 fieldId 或 fieldIds 对应。")
        UUID id,
        @Schema(description = "模型字段稳定编码，也是读取物理表后 Spark DataFrame 中的列名。")
        String code,
        @Schema(description = "模型字段展示名称。")
        String name,
        @Schema(description = "字段平台数据类型；决定该字段可用于哪些规则及常量的解析格式。")
        PlatformDataType fieldType,
        @Schema(description = "字段当前绑定的标准字典摘要；未绑定字典时为空。DICTIONARY_MEMBERSHIP 运行时使用该字典的有效值快照。")
        StandardDictionarySummaryResponse standardDictionary
) {
    public static ModelQualityRuleFieldResponse from(
            DataModelField field,
            StandardDictionarySummaryResponse standardDictionary
    ) {
        return new ModelQualityRuleFieldResponse(
                field.getId(), field.getCode(), field.getName(), field.getFieldType(), standardDictionary
        );
    }
}
