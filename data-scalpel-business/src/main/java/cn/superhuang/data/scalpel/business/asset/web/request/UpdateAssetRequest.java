package cn.superhuang.data.scalpel.business.asset.web.request;

import cn.superhuang.data.scalpel.business.asset.domain.AssetSensitivityLevel;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateAssetRequest(
        UUID directoryId,
        @Size(max = 100) String portalName,
        @Size(max = 1000) String portalSummary,
        @Size(max = 10) List<@Size(max = 30) String> tags,
        @Size(max = 100) String ownerName,
        @Size(max = 100) String updateFrequency,
        AssetSensitivityLevel sensitivityLevel,
        boolean featured
) {
}
