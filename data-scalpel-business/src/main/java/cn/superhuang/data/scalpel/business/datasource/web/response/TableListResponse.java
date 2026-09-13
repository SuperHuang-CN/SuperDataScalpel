package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableList;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "数据库表和视图列表")
public record TableListResponse(
        @Schema(description = "当前命名空间内返回的表和视图摘要") List<TableSummaryResponse> tables,
        @Schema(description = "结果是否因服务端数量上限而被截断；为 true 时列表不完整") boolean truncated
) {
    public static TableListResponse from(TableList tableList) {
        return new TableListResponse(
                tableList.tables().stream().map(TableSummaryResponse::from).toList(),
                tableList.truncated()
        );
    }
}
