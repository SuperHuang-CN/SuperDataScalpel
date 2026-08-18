package cn.superhuang.data.scalpel.business.asset.web.resource;

import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import cn.superhuang.data.scalpel.business.asset.service.AssetPortalService;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalAssetDetailResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalAssetSummaryResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalOverviewResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/asset-portal")
@Tag(name = "数据资产门户")
public class AssetPortalResource {

    private final AssetPortalService service;

    public AssetPortalResource(AssetPortalService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @Operation(summary = "查询公开资产门户概览")
    public AssetPortalOverviewResponse overview() {
        return service.overview();
    }

    @GetMapping("/assets")
    @Operation(summary = "查询公开资产")
    public PageResponse<AssetPortalAssetSummaryResponse> assets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(required = false) UUID directoryId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return service.assets(keyword, assetType, directoryId, page, size);
    }

    @GetMapping("/assets/{id}")
    @Operation(summary = "查询公开资产详情")
    public AssetPortalAssetDetailResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }
}
