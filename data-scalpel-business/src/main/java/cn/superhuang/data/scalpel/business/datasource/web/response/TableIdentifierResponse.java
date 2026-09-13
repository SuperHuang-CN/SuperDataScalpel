package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "数据库表的限定标识")
public record TableIdentifierResponse(
        @Schema(description = "Catalog 名称；数据库不使用 Catalog 时为空") String catalog,
        @Schema(description = "Schema 名称；数据库不使用 Schema 时为空") String schema,
        @Schema(description = "表或视图名称") String table
) {
    public static TableIdentifierResponse from(TableIdentifier identifier) {
        return new TableIdentifierResponse(identifier.catalog(), identifier.schema(), identifier.table());
    }
}
