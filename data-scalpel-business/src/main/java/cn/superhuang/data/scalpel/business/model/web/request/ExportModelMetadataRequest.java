package cn.superhuang.data.scalpel.business.model.web.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ExportModelMetadataRequest(
        @NotEmpty @Size(max = 200) List<@NotNull UUID> modelIds
) {
}
