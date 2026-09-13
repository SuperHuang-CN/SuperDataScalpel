package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "文件逻辑表字段到目标数据存储的 MANAGED 模型字段候选")
public record FileDatasetImportColumnResponse(
        @Schema(description = "文件数据集逻辑表中的原始字段名") String sourceName,
        @Schema(description = "文件解析阶段保存的平台类型定义") PlatformTypeDefinition sourceType,
        @Schema(description = "字段编码候选；只小写化，非法或重复时可能为空") String code,
        @Schema(description = "字段显示名称候选") String name,
        @Schema(description = "目标模型的平台数据类型；未解决时为空") PlatformDataType fieldType,
        @Schema(description = "映射为 STRING 时的最大字符数；无显式上限或其他平台类型时为空。") Integer length,
        @Schema(description = "映射为 DECIMAL 时的总有效位数；其他平台类型为空") Integer precision,
        @Schema(description = "映射为 DECIMAL 时的小数位数；其他平台类型为空") Integer scale,
        @Schema(description = "Geometry 定义；标量字段为空") GeometryTypeDefinition geometry,
        @Schema(description = "文件解析阶段保存的字段可空性。") boolean nullable,
        @Schema(description = "模型主键候选；当前文件逻辑表导入固定返回 false，不从文件 Schema 推断主键。") boolean primaryKey,
        @Schema(description = "文件逻辑表字段保存的排序值，原样作为字段显示和建表顺序候选。") int sortOrder,
        @Schema(description = "字段说明候选；当前文件逻辑表导入固定为空，需由用户在创建草稿前补充。") String description,
        @Schema(description = "平台类型到目标物理类型的映射质量") TypeMappingQuality mappingQuality,
        @Schema(description = "目标方言映射结果或降级原因") String mappingMessage,
        @Schema(description = "该字段候选是否完整且能由目标方言安全表达") boolean importable,
        @Schema(description = "字段级名称、类型或 Geometry 问题") List<String> issues
) {

    public FileDatasetImportColumnResponse {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
