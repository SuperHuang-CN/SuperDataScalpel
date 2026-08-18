package cn.superhuang.data.scalpel.business.asset.web.request;

import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record RegisterAssetsRequest(
        @NotNull AssetType assetType,
        @NotEmpty @Size(max = 100) List<@NotNull UUID> resourceIds
) {
}
