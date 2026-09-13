package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "模型严格匹配物理表的固定上限快速预览；不接受筛选、分页或总数统计。服务端会在可用时按 ClickHouse ORDER BY 和模型主键排序，否则样例顺序不稳定。")
public record DataModelPreviewResponse(
        @Schema(description = "物理表 Catalog；数据库不使用 Catalog 时为空") String catalogName,
        @Schema(description = "物理表 Schema；数据库不使用 Schema 时为空") String schemaName,
        @Schema(description = "本次只读预览实际访问的物理表名称") String tableName,
        @Schema(description = "实际返回的可预览列") List<Column> columns,
        @Schema(description = "最多 50 行样例数据；不包含 BINARY 或 GEOMETRY。DECIMAL 转为字符串，日期时间转为 ISO 字符串。") List<Map<String, Object>> rows,
        @Schema(description = "本次预览采用的行数上限，当前固定最多 50") int limit,
        @Schema(description = "是否还存在未返回的数据；true 表示 rows 只是样本，不是完整表数据。") boolean truncated
) {
    @Schema(description = "快速预览结果列")
    public record Column(
            @Schema(description = "模型字段编码，也是行对象中的键") String code,
            @Schema(description = "字段显示名称") String name,
            @Schema(description = "平台数据类型") String fieldType
    ) {
    }

    public static DataModelPreviewResponse from(
            DataModel model,
            List<DataModelField> fields,
            List<Map<String, Object>> rows,
            int limit,
            boolean truncated
    ) {
        return new DataModelPreviewResponse(
                model.getCatalogName(),
                model.getSchemaName(), model.getPhysicalTableName(),
                fields.stream().map(field -> new Column(field.getCode(), field.getName(), field.getFieldType().name())).toList(),
                rows, limit, truncated
        );
    }
}
