package cn.superhuang.data.scalpel.business.model.web.response;

import java.util.UUID;

public record ImportedModelMetadataResponse(
        UUID id,
        String code,
        String name,
        UUID directoryId
) {
}
