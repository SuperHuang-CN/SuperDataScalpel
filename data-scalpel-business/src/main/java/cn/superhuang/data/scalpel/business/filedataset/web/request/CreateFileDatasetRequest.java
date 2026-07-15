package cn.superhuang.data.scalpel.business.filedataset.web.request;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFileDatasetRequest(
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @NotNull FileDatasetFormat format,
        @Size(max = 1000) String description
) {
}
