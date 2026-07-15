package cn.superhuang.data.scalpel.business.filedataset.web.request;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import jakarta.validation.constraints.NotNull;

public record ReplaceFileDatasetContentRequest(@NotNull FileDatasetFormat format) {
}
