package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.ModelFieldTemplateItem;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "常用字段模板中的一个可复制字段快照")
public record ModelFieldTemplateFieldResponse(
        @Schema(description = "模板字段 UUID") UUID id,
        @Schema(description = "模板内唯一字段编码") String code,
        @Schema(description = "字段显示名称") String name,
        @Schema(description = "平台数据类型") PlatformDataType fieldType,
        @Schema(description = "STRING 模板字段的可选最大字符数；其他平台类型为空。") Integer length,
        @Schema(description = "DECIMAL 模板字段的总有效位数；其他平台类型为空") Integer precision,
        @Schema(description = "DECIMAL 模板字段的小数位数；其他平台类型为空") Integer scale,
        @Schema(description = "Geometry 定义；标量字段为空") GeometryTypeDefinition geometry,
        @Schema(description = "复制到模型后的字段是否允许 NULL") boolean nullable,
        @Schema(description = "复制到模型后的字段是否属于主键") boolean primaryKey,
        @Schema(description = "模板内字段排序；相同值再按字段编码排列。") int sortOrder,
        @Schema(description = "字段业务含义、单位、口径或值域说明") String description,
        @Schema(description = "模板字段当前绑定的码表摘要；码表已停用时仍可能返回且 enabled=false，码表已删除时为空。调用方复制进模型请求时由模型接口重新校验能否绑定。") StandardDictionarySummaryResponse standardDictionary
) {
    public static ModelFieldTemplateFieldResponse from(
            ModelFieldTemplateItem item,
            StandardDictionarySummaryResponse standardDictionary
    ) {
        return new ModelFieldTemplateFieldResponse(
                item.getId(), item.getCode(), item.getName(), item.getFieldType(),
                item.getLength(), item.getPrecision(), item.getScale(), item.getGeometry(),
                item.isNullable(), item.isPrimaryKey(), item.getSortOrder(), item.getDescription(),
                standardDictionary
        );
    }
}
