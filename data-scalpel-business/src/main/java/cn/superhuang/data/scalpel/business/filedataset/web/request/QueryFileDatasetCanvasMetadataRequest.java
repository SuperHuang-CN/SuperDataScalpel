package cn.superhuang.data.scalpel.business.filedataset.web.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Read-only batch lookup used by the Canvas designer. */
public record QueryFileDatasetCanvasMetadataRequest(
        @NotEmpty
        @Size(max = 200)
        List<UUID> fileDatasetTableIds
) {
}
