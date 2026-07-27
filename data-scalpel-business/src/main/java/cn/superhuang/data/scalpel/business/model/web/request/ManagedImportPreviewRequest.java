package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ManagedImportPreviewRequest(
        @NotNull UUID sourceDataSourceId,
        @NotNull @Valid ManagedImportTableIdentifierInput sourceTable,
        @NotNull UUID targetStorageDataSourceId
) {
}
