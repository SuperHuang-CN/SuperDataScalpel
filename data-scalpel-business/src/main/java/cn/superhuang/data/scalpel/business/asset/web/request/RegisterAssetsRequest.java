package cn.superhuang.data.scalpel.business.asset.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "把一批同类型业务资源登记为资产草稿；不会修改或发布来源资源。")

public record RegisterAssetsRequest(
        @Schema(description = "来源资源类型，决定 resourceIds 应指向模型、数据服务、文件数据集或全景影像。")
        @NotNull AssetType assetType,
        @Schema(description = "要登记的来源业务资源 UUID 列表，最多 100 项；每项类型必须与 assetType 一致。")
        @NotEmpty @Size(max = 100) List<@NotNull UUID> resourceIds
) {
}
