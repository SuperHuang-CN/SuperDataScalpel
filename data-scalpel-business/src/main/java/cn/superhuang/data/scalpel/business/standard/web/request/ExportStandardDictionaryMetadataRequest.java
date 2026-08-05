package cn.superhuang.data.scalpel.business.standard.web.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ExportStandardDictionaryMetadataRequest(
        @NotEmpty @Size(max = 200) List<UUID> dictionaryIds
) {
}
