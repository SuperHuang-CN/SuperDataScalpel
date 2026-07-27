package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ManagedImportTableIdentifierInput(
        @Size(max = 128) String catalog,
        @Size(max = 128) String schema,
        @NotBlank @Size(max = 128) String table
) {

    public TableIdentifier toIdentifier() {
        return new TableIdentifier(normalize(catalog), normalize(schema), table.trim());
    }

    private static String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
