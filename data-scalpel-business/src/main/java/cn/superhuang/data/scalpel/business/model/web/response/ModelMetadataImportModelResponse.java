package cn.superhuang.data.scalpel.business.model.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Excel 模型行及其字段的规范化内容和校验结果")
public record ModelMetadataImportModelResponse(
        @Schema(description = "本次预览内定位模型行的键，格式为 model:<Excel行号>；服务端不保存该 key，正式导入请求也不使用它。") String key,
        @Schema(description = "模型 Sheet 中的原始 Excel 行号") int rowNumber,
        @Schema(description = "模型编码，已去除首尾空白并转为小写；导入不会按分层自动添加前缀。") String code,
        @Schema(description = "模型显示名称") String name,
        @Schema(description = "MODEL 目录完整路径；空表示未分类，目录不存在时阻止导入") String directoryPath,
        @Schema(description = "Excel 中填写并转为大写后的数仓分层编码；未填写时为空，分层名称列不参与匹配。") String warehouseLayerCode,
        @Schema(description = "按编码匹配到的数仓分层；已停用分层仍会返回详情并同时产生 issue，不存在时为空。") ModelWarehouseLayerSummaryResponse warehouseLayer,
        @Schema(description = "目标物理表名，已去除首尾空白并转为小写。") String physicalTableName,
        @Schema(description = "Excel 中解析的 ClickHouse MergeTree 排序键字段编码") List<String> clickHouseOrderByColumns,
        @Schema(description = "模型业务说明") String description,
        @Schema(description = "模型及全部字段是否已通过校验，可以提交导入") boolean importable,
        @Schema(description = "阻止导入的模型编码、目录、分层、物理位置或字段问题") List<String> issues,
        @Schema(description = "旧格式缺失目录、分层或码表能力，以及目标不是 ClickHouse 时排序键被清空等非阻断提示。") List<String> warnings,
        @Schema(description = "该模型的逐字段校验结果") List<ModelMetadataImportFieldResponse> fields
) {

    public ModelMetadataImportModelResponse {
        clickHouseOrderByColumns = List.copyOf(clickHouseOrderByColumns);
        issues = List.copyOf(issues);
        warnings = List.copyOf(warnings);
        fields = List.copyOf(fields);
    }
}
