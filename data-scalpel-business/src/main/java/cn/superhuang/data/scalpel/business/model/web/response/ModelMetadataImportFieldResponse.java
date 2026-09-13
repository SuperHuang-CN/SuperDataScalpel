package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Excel 字段行的规范化内容、码表匹配和校验结果")
public record ModelMetadataImportFieldResponse(
        @Schema(description = "本次预览内定位字段行的键，格式为 field:<Excel行号>；服务端不保存该 key，正式导入请求也不使用它。") String key,
        @Schema(description = "字段 Sheet 中的原始 Excel 行号") int rowNumber,
        @Schema(description = "字段编码；空或非法时需在校对中修正") String code,
        @Schema(description = "字段显示名称") String name,
        @Schema(description = "解析后的平台数据类型") PlatformDataType fieldType,
        @Schema(description = "STRING 字段的可选最大字符数；其他平台类型为空。") Integer length,
        @Schema(description = "DECIMAL 字段的总有效位数；其他平台类型为空") Integer precision,
        @Schema(description = "DECIMAL 字段的小数位数；其他平台类型为空") Integer scale,
        @Schema(description = "Geometry 定义；标量字段为空") GeometryTypeDefinition geometry,
        @Schema(description = "字段是否允许 NULL；无法解析时为空") Boolean nullable,
        @Schema(description = "字段是否属于主键；无法解析时为空") Boolean primaryKey,
        @Schema(description = "字段显示和建表顺序；无法解析时为空") Integer sortOrder,
        @Schema(description = "字段业务说明") String description,
        @Schema(description = "Excel 中填写并规范化后的码表编码；未填写时为空") String standardDictionaryCode,
        @Schema(description = "匹配到且通过启用状态和值类型兼容校验的码表摘要；未填写、不存在、停用或不兼容时为空并在相应情况下产生 issue。") StandardDictionarySummaryResponse standardDictionary,
        @Schema(description = "该字段行是否已通过全部校验") boolean importable,
        @Schema(description = "字段标识、类型、Geometry、主键或码表问题") List<String> issues
) {

    public ModelMetadataImportFieldResponse {
        issues = List.copyOf(issues);
    }
}
