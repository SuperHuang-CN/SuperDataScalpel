package cn.superhuang.data.scalpel.business.asset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.asset.domain.AssetType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Schema(description = "匿名资产门户的已发布资产统计")

public record AssetPortalOverviewResponse(
        @Schema(description = "匿名门户当前可见的已发布资产总数。")
        long totalCount,
        @Schema(description = "按资产类型统计的数量；键为资产类型枚举值，值为该类型资产数。")
        Map<AssetType, Long> typeCounts,
        @Schema(description = "按已发布资产使用频度汇总的热门标签列表；没有标签时为空列表。")
        List<String> popularTags,
        @Schema(description = "顶级业务目录及其后代中的已发布资产统计；没有可见业务目录时为空列表。")
        List<Domain> domains
) {
    @Schema(description = "顶级业务领域及其全部后代中的已发布资产数量")
    public record Domain(
            @Schema(description = "顶级业务目录 UUID，可用于筛选门户资产。")
            UUID id,
            @Schema(description = "顶级业务目录名称。")
            String name,
            @Schema(description = "用途说明；未填写时为空。")
            String description,
            @Schema(description = "该顶级业务领域自身及全部后代领域中的 PUBLISHED 资产数量。")
            long assetCount
    ) {
    }
}
