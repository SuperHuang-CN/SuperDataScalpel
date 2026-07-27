package cn.superhuang.data.scalpel.business.filedataset.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFileDatasetTableRequest(@NotBlank @Size(max = 255) String name) {
}
