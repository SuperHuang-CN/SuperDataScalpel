package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalColumnRole;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "模型字段的数据库无关结构和业务元数据")
public record DataModelFieldResponse(
        @Schema(description = "稳定字段 UUID，供任务、服务、指标和血缘引用") UUID id,
        @Schema(description = "所属模型 UUID") UUID modelId,
        @Schema(description = "模型内唯一字段编码，也是物理列名") String code,
        @Schema(description = "字段显示名称") String name,
        @Schema(description = "数据库无关的平台数据类型") PlatformDataType fieldType,
        @Schema(description = "STRING 或 BINARY 字段的最大长度；其他平台类型为空") Integer length,
        @Schema(description = "DECIMAL 字段的总有效位数；其他平台类型为空") Integer precision,
        @Schema(description = "DECIMAL 字段的小数位数；其他平台类型为空") Integer scale,
        @Schema(description = "GEOMETRY 子类型、EPSG CRS 和坐标维度；标量字段为空") GeometryTypeDefinition geometry,
        @Schema(description = "物理列是否允许 NULL") boolean nullable,
        @Schema(description = "字段是否属于模型主键") boolean primaryKey,
        @Schema(description = "字段展示和受管建表顺序") int sortOrder,
        @Schema(description = "字段业务含义、单位、口径或值域说明") String description,
        @Schema(description = "外部 TDengine 超级表中的 TIME_KEY、TAG 或 REGULAR 角色；其他模型通常为 REGULAR") DataModelPhysicalColumnRole physicalColumnRole,
        @Schema(description = "绑定的树形码表摘要；未绑定时为空，绑定不参与 DDL") StandardDictionarySummaryResponse standardDictionary,
        @Schema(description = "字段创建时间") Instant createdAt,
        @Schema(description = "字段最后更新时间") Instant updatedAt
) {
    public static DataModelFieldResponse from(DataModelField field) {
        return from(field, null);
    }

    public static DataModelFieldResponse from(
            DataModelField field,
            StandardDictionarySummaryResponse standardDictionary
    ) {
        return new DataModelFieldResponse(
                field.getId(), field.getModelId(), field.getCode(), field.getName(), field.getFieldType(),
                field.getLength(), field.getPrecision(), field.getScale(), field.getGeometry(),
                field.isNullable(), field.isPrimaryKey(),
                field.getSortOrder(), field.getDescription(), field.getPhysicalColumnRole(), standardDictionary,
                field.getCreatedAt(), field.getUpdatedAt()
        );
    }
}
