package cn.superhuang.data.scalpel.business.filedataset.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateFileDatasetRequest(
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @jakarta.validation.constraints.NotNull @Valid FileDatasetParsingOptionsRequest parsingOptions,
        @Size(max = 1000) String description
) {
}
