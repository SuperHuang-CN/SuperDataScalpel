package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "只读 JDBC 查询的结构探测结果；驱动无法直接提供 PreparedStatement 元数据时会执行至多 1 行，但响应不返回业务数据")
public record JdbcQueryInspectionResponse(
        @Schema(description = "规范化后被分析 SQL 的 SHA-256，用于确认结果对应的查询文本") String analyzedSqlSha256,
        @Schema(description = "查询结果列及平台类型，供任务节点生成输出 Schema") List<CanvasColumnSchema> columns
) {
    public JdbcQueryInspectionResponse {
        columns = List.copyOf(columns);
    }
}
