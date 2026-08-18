package cn.superhuang.data.scalpel.business.asset.web.resource;

import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import cn.superhuang.data.scalpel.business.asset.service.AssetManagementService;
import cn.superhuang.data.scalpel.business.asset.service.AssetPortalService;
import cn.superhuang.data.scalpel.business.asset.web.request.RegisterAssetsRequest;
import cn.superhuang.data.scalpel.business.asset.web.request.UpdateAssetRequest;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetBatchOperationResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetCandidateResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetSourceNavigationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
@Tag(name = "资产管理")
public class AssetResource {

    private final AssetManagementService service;
    private final AssetPortalService portalService;

    public AssetResource(AssetManagementService service, AssetPortalService portalService) {
        this.service = service;
        this.portalService = portalService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('asset.view')")
    @Operation(summary = "查询资产")
    public PageResponse<AssetResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('asset.view')")
    @Operation(summary = "查询资产详情")
    public AssetResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/source-navigation")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取资产来源导航")
    public AssetSourceNavigationResponse sourceNavigation(@PathVariable UUID id, Authentication authentication) {
        return portalService.sourceNavigation(id, authentication);
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "查询可登记的来源资源")
    public PageResponse<AssetCandidateResponse> candidates(
            @RequestParam AssetType assetType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return service.candidates(assetType, keyword, page, size);
    }

    @PostMapping("/actions/register")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "批量登记资产草稿")
    public List<AssetResponse> register(@Valid @RequestBody RegisterAssetsRequest request) {
        return service.register(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "修改资产门户信息")
    public AssetResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAssetRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "发布资产")
    public AssetResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/actions/offline")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "下线资产")
    public AssetResponse offline(@PathVariable UUID id) {
        return service.offline(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "删除资产记录")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/actions/check")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "检查资产来源变化")
    public AssetResponse check(@PathVariable UUID id) {
        return service.check(id);
    }

    @PostMapping("/{id}/actions/sync")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "同步资产来源快照")
    public AssetResponse sync(@PathVariable UUID id) {
        return service.sync(id);
    }

    @PostMapping("/actions/check-all")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "全量检查已登记资产")
    public AssetBatchOperationResponse checkAll() {
        return service.checkAll();
    }

    @PostMapping("/actions/sync-all")
    @PreAuthorize("hasAuthority('asset.manage')")
    @Operation(summary = "全量同步已登记资产")
    public AssetBatchOperationResponse syncAll() {
        return service.syncAll();
    }
}
