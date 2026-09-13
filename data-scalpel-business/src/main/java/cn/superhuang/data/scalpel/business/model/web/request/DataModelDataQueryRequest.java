package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * SQL-free conditional query for one model's resolved physical table.
 * Grouping and aggregation intentionally remain outside the first model-preview release.
 */
@Schema(description = "模型严格匹配物理表的 SQL-free 只读条件查询；不支持任意 SQL、分组或聚合。单次数据库查询超时为 15 秒，分页偏移量最多 10,000。")
public record DataModelDataQueryRequest(
        @Schema(description = "页码，从 1 开始；省略时使用第一页") @Min(1) Integer pageNo,
        @Schema(description = "每页条数，范围 1 到 100；省略使用 50。服务端实际多读取 1 行来计算 hasNext。") @Min(1) Integer pageSize,
        @Schema(description = "顶层筛选条件连接方式：AND 或 OR；省略时使用 AND") @Pattern(regexp = "AND|OR") String conditionType,
        @Schema(description = "要返回的唯一模型字段编码并决定响应列顺序；省略或空数组返回全部可查询字段。BINARY 和 GEOMETRY 不可返回；未知、重复或最终没有可返回字段时拒绝。") List<String> columns,
        @Schema(description = "顶层筛选条件，最多 20 个；省略为空数组且不过滤。BINARY 和 GEOMETRY 字段不能筛选。") List<@Valid DataModelDataQueryFilterInput> filters,
        @Schema(description = "显式排序项，最多 3 个。服务端随后追加未包含的主键升序；ClickHouse 未显式排序时先使用模型 MergeTree ORDER BY 字段，再追加主键。没有任何可用排序时响应 stableOrder=false。") List<@Valid DataModelDataQueryOrderInput> orders,
        @Schema(description = "是否额外执行同条件 COUNT 查询并返回 totalCount；省略或 false 时不计数，totalCount 为空，hasNext 仍通过多取一行判断。") Boolean returnCount
) {

    public DataModelDataQueryRequest {
        columns = columns == null ? List.of() : List.copyOf(columns);
        filters = filters == null ? List.of() : List.copyOf(filters);
        orders = orders == null ? List.of() : List.copyOf(orders);
    }
}
