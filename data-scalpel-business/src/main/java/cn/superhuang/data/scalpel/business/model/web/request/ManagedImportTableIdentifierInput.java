package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "JDBC 来源表的限定标识")
public record ManagedImportTableIdentifierInput(
        @Schema(description = "来源 Catalog；数据库不使用 Catalog 时为空") @Size(max = 128) String catalog,
        @Schema(description = "来源 Schema；数据库不使用 Schema 时为空") @Size(max = 128) String schema,
        @Schema(description = "来源普通表名称") @NotBlank @Size(max = 128) String table
) {

    public TableIdentifier toIdentifier() {
        return new TableIdentifier(normalize(catalog), normalize(schema), table.trim());
    }

    private static String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
