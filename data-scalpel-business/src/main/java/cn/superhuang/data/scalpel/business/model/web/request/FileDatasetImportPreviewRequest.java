package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FileDatasetImportPreviewRequest(
        @NotNull UUID fileDatasetId,
        @NotNull UUID fileDatasetTableId,
        @NotNull UUID targetStorageDataSourceId
) {
}
