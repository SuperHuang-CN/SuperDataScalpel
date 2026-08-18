package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ImportModelMetadataRequest(
        @NotNull UUID targetStorageDataSourceId,
        @NotEmpty @Size(max = 200) List<@Valid ImportModelMetadataModelRequest> models
) {

    public ImportModelMetadataRequest {
        models = models == null ? List.of() : List.copyOf(models);
    }
}
