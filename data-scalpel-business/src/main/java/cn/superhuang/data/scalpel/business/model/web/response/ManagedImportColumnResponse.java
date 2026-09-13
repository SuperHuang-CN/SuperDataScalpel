package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "JDBC 来源列经平台类型再到目标方言的 MANAGED 字段候选")
public record ManagedImportColumnResponse(
        @Schema(description = "来源物理列名") String sourceName,
        @Schema(description = "来源数据库原生类型") String nativeType,
        @Schema(description = "字段编码候选；只小写化，非法或重复时可能为空") String code,
        @Schema(description = "字段显示名称候选") String name,
        @Schema(description = "安全映射后的平台数据类型；未解决时为空") PlatformDataType fieldType,
        @Schema(description = "映射为 STRING 时的最大字符数；无显式上限或其他平台类型时为空。") Integer length,
        @Schema(description = "映射为 DECIMAL 时的总有效位数；其他平台类型为空") Integer precision,
        @Schema(description = "映射为 DECIMAL 时的小数位数；其他平台类型为空") Integer scale,
        @Schema(description = "Geometry 定义；标量字段为空") GeometryTypeDefinition geometry,
        @Schema(description = "字段是否允许 NULL") boolean nullable,
        @Schema(description = "字段是否属于来源表主键；GEOMETRY 主键会使候选不可导入。") boolean primaryKey,
        @Schema(description = "字段显示和受管建表顺序；来源列 ordinal 为正时使用 ordinal×10，否则使用返回顺序×10。") int sortOrder,
        @Schema(description = "来源列注释去除首尾空白后形成的字段说明候选，最长 500 字符；未提供时为空。") String description,
        @Schema(description = "来源物理类型到平台类型、再到目标物理类型的综合映射质量") TypeMappingQuality mappingQuality,
        @Schema(description = "类型映射结果或降级原因") String mappingMessage,
        @Schema(description = "该字段候选是否完整且可由目标方言安全表达") boolean importable,
        @Schema(description = "字段级标识符、类型或结构问题") List<String> issues
) {

    public ManagedImportColumnResponse {
        issues = List.copyOf(issues);
    }
}
