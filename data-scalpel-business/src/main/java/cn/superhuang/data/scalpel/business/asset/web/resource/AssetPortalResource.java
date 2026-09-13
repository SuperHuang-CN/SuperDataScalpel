package cn.superhuang.data.scalpel.business.asset.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;
import cn.superhuang.data.scalpel.business.asset.service.AssetPortalService;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalAssetDetailResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalAssetSummaryResponse;
import cn.superhuang.data.scalpel.business.asset.web.response.AssetPortalOverviewResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@org.springframework.security.access.prepost.PreAuthorize("permitAll()")
@RestController
@RequestMapping("/api/v1/asset-portal")
@Tag(name = "数据资产门户")
public class AssetPortalResource {

    private final AssetPortalService service;

    public AssetPortalResource(AssetPortalService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询公开资产门户概览",
            keywords = {"资产门户", "已发布资产", "类型统计", "热门标签", "业务领域"})
    @GetMapping("/overview")
    @Operation(summary = "查询公开资产门户概览", description = "匿名返回已发布资产总数、按五种来源类型的数量、热门真实标签，以及顶级业务领域连同后代领域的已发布资产数量。")
    public AssetPortalOverviewResponse overview() {
        return service.overview();
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询公开资产",
            keywords = {"资产门户", "搜索资产", "已发布资产", "业务领域"},
            relatedOperations = {"GET /api/v1/asset-portal/assets/{id}"})
    @GetMapping("/assets")
    @Operation(summary = "查询公开资产", description = "匿名分页查询 PUBLISHED 资产。关键词匹配有效名称、简介、来源编码和标签；directoryId 包含该业务领域全部后代。结果固定按推荐、发布时间和 UUID 倒序。")
    public PageResponse<AssetPortalAssetSummaryResponse> assets(
            @Parameter(description = "可选关键词，模糊匹配资产有效名称、有效简介、来源稳定编码或任一标签；为空时不按关键词筛选。") @RequestParam(required = false) String keyword,
            @Parameter(description = "可选资产类型筛选。") @RequestParam(required = false) AssetType assetType,
            @Parameter(description = "可选 ASSET 范围业务领域目录 UUID；提供时包含该目录及全部后代目录中的资产。") @RequestParam(required = false) UUID directoryId,
            @Parameter(description = "页码，从 0 开始；省略时为 0。") @RequestParam(required = false) Integer page,
            @Parameter(description = "每页数量，默认 12，最大 48。") @RequestParam(required = false) Integer size
    ) {
        return service.assets(keyword, assetType, directoryId, page, size);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询公开资产详情",
            keywords = {"资产门户", "资产详情", "实时来源", "缓存快照"},
            relatedOperations = {"GET /api/v1/assets/{id}/source-navigation"})
    @GetMapping("/assets/{id}")
    @Operation(summary = "查询公开资产详情", description = "匿名返回已发布资产的公开治理信息和安全来源元数据。来源可用时使用实时值；来源缺失或读取失败时回退最近成功快照。读取不会修改同步状态。")
    public AssetPortalAssetDetailResponse detail(@Parameter(description = "已发布资产 UUID。") @PathVariable UUID id) {
        return service.detail(id);
    }
}
