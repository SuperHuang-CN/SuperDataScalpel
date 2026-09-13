package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据库表或视图摘要")
public record TableSummaryResponse(
        @Schema(description = "表的 Catalog、Schema 与名称") TableIdentifierResponse identifier,
        @Schema(description = "数据库对象类型，例如 TABLE 或 VIEW") String type,
        @Schema(description = "数据库对象注释") String comment
) {
    static TableSummaryResponse from(TableSummary table) {
        return new TableSummaryResponse(TableIdentifierResponse.from(table.identifier()), table.type(), table.comment());
    }
}
