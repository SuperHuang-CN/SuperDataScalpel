package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** Result of a read-only physical-table query issued through model management. */
@Schema(description = "模型严格匹配物理表的受控只读查询结果；查询最多等待 15 秒，返回值经过 JDBC 安全规范化。")
public record DataModelDataQueryResponse(
        @Schema(description = "返回列定义；每行对象以 code 为键") List<Column> columns,
        @Schema(description = "当前页行对象；不包含 BINARY 或 GEOMETRY。DECIMAL 转为不丢精度的十进制字符串，DATE 与 TIMESTAMP 转为 ISO 字符串，其他标量保持 JSON 可表示值。") List<Map<String, Object>> rows,
        @Schema(description = "当前页码，从 1 开始") int pageNo,
        @Schema(description = "采用的每页上限，范围 1 到 100；rows 在最后一页可能少于该值。") int pageSize,
        @Schema(description = "是否存在下一页；默认通过多取一行判断") boolean hasNext,
        @Schema(description = "匹配总数；仅 returnCount=true 时返回，否则为空") Long totalCount,
        @Schema(description = "最终 SQL 是否包含明确排序；显式排序、ClickHouse ORDER BY 或模型主键均可使其为 true，false 表示跨页顺序可能不稳定。") boolean stableOrder
) {

    @Schema(description = "按请求字段顺序返回的结果列定义")
    public record Column(
            @Schema(description = "模型字段编码，也是行对象中的键") String code,
            @Schema(description = "字段显示名称") String name,
            @Schema(description = "平台数据类型") String fieldType
    ) {
        public static Column from(DataModelField field) {
            return new Column(field.getCode(), field.getName(), field.getFieldType().name());
        }
    }

    public DataModelDataQueryResponse {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }
}
