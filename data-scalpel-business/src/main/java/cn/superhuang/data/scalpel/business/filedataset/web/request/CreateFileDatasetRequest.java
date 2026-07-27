package cn.superhuang.data.scalpel.business.filedataset.web.request;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFileDatasetRequest(
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @NotNull FileDatasetType type,
        @NotNull @Valid FileDatasetParsingOptionsRequest parsingOptions,
        @Size(max = 1000) String description
) {
}
