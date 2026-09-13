package cn.superhuang.data.scalpel.business.asset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "从资产管理页跳转到来源业务资源的前端导航信息。")

public record AssetSourceNavigationResponse(
        @Schema(description = "来源资源在当前管理前端中的站内路由路径。")
        String path
) {
}
