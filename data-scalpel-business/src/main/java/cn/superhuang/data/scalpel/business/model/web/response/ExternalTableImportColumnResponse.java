package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "外部物理表一列到模型字段的映射预览")
public record ExternalTableImportColumnResponse(
        @Schema(description = "来源物理列名，同时作为字段编码") String name,
        @Schema(description = "数据库声明的原生类型") String nativeType,
        @Schema(description = "映射后的平台数据类型；无法安全映射时为空") PlatformDataType platformType,
        @Schema(description = "映射为 STRING 或 BINARY 时的最大长度；其他平台类型为空") Integer length,
        @Schema(description = "映射为 DECIMAL 时的总有效位数；其他平台类型为空") Integer precision,
        @Schema(description = "映射为 DECIMAL 时的小数位数；其他平台类型为空") Integer scale,
        @Schema(description = "映射后的 Geometry 定义；标量列为空") GeometryTypeDefinition geometry,
        @Schema(description = "来源物理列是否允许 NULL") boolean nullable,
        @Schema(description = "来源物理列是否属于主键") boolean primaryKey,
        @Schema(description = "物理类型到平台类型的映射质量：EXACT、LOSSY 或 UNSUPPORTED") TypeMappingQuality mappingQuality,
        @Schema(description = "映射质量或无法导入原因说明") String message,
        @Schema(description = "该列能否无损纳入外部模型；任一列不可导入会阻止整张表绑定") boolean importable,
        @Schema(description = "数据库列注释，将作为模型字段说明候选") String comment,
        @Schema(description = "TDengine 超级表列的 TIME_KEY、TAG 或 REGULAR 角色；其他数据库为 REGULAR") String physicalColumnRole
) {
}
