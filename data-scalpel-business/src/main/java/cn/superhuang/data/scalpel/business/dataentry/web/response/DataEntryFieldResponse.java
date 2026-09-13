package cn.superhuang.data.scalpel.business.dataentry.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.UUID;

@Schema(description = "填报表单中的模型字段、类型约束和输入值来源。")

public record DataEntryFieldResponse(
        @Schema(description = "目标模型字段 UUID。")
        UUID id,
        @Schema(description = "提交 values 和业务主键映射时使用的字段稳定编码。")
        String code,
        @Schema(description = "表单展示的字段名称。")
        String name,
        @Schema(description = "字段的平台数据类型。")
        PlatformDataType fieldType,
        @Schema(description = "STRING 或 BINARY 字段允许的最大长度；其他平台类型为空。")
        Integer length,
        @Schema(description = "DECIMAL 字段的总有效位数；其他平台类型为空。")
        Integer precision,
        @Schema(description = "DECIMAL 字段的小数位数；其他平台类型为空。")
        Integer scale,
        @Schema(description = "GEOMETRY 字段的几何类型、坐标维度和坐标参考系定义；非几何字段为空。")
        GeometryTypeDefinition geometry,
        @Schema(description = "字段是否允许保存空值。")
        boolean nullable,
        @Schema(description = "是否为主键字段。")
        boolean primaryKey,
        @Schema(description = "同级展示顺序，数值越小越靠前。")
        int sortOrder,
        @Schema(description = "模型字段用途或填写说明；未填写时为空。")
        String description,
        @Schema(description = "字段输入方式：DEFAULT 为普通输入，DICTIONARY 从标准字典选值，MODEL_LOOKUP 从关联模型选值。")
        String inputSource,
        @Schema(description = "DICTIONARY 输入方式绑定的标准字典摘要；其他输入方式为空。")
        StandardDictionarySummaryResponse standardDictionary,
        @Schema(description = "MODEL_LOOKUP 输入方式使用的来源模型和值/标签字段配置；其他输入方式为空。")
        DataEntryLookupResponse lookup
) {
    public static DataEntryFieldResponse from(
            DataModelField field,
            StandardDictionarySummaryResponse dictionary,
            DataEntryLookupResponse lookup
    ) {
        return new DataEntryFieldResponse(
                field.getId(), field.getCode(), field.getName(), field.getFieldType(), field.getLength(),
                field.getPrecision(), field.getScale(), field.getGeometry(), field.isNullable(), field.isPrimaryKey(),
                field.getSortOrder(), field.getDescription(), dictionary != null ? "DICTIONARY" : lookup != null ? "MODEL_LOOKUP" : "DEFAULT",
                dictionary, lookup
        );
    }
}
