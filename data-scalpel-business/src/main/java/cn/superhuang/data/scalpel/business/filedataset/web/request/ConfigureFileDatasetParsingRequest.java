package cn.superhuang.data.scalpel.business.filedataset.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ConfigureFileDatasetParsingRequest(
        @NotNull @Valid FileDatasetParsingOptionsRequest options
) {
}
