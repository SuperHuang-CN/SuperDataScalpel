package cn.superhuang.data.scalpel.business.asset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "全量检查或同步所有已登记资产后的逐项容错汇总；各状态计数之和等于 totalCount，单项失败不回滚其他资产。")

public record AssetBatchOperationResponse(
        @Schema(description = "本次全量检查或同步实际纳入处理的资产总数。")
        int totalCount,
        @Schema(description = "最终同步状态为 IN_SYNC 的资产数；检查时表示指纹一致，同步时表示快照成功更新。")
        int successCount,
        @Schema(description = "来源仍存在但资产快照已落后于来源版本的资产数。")
        int outdatedCount,
        @Schema(description = "来源仍存在但当前业务状态不再满足资产注册条件的资产数。")
        int unavailableCount,
        @Schema(description = "对应来源业务资源已经不存在的资产数。")
        int missingCount,
        @Schema(description = "因未预期错误而未完成本次检查或同步的资产数。")
        int failedCount,
        @Schema(description = "失败项列表；没有时为空列表。")
        List<Failure> failures
) {
    @Schema(description = "因未预期错误而未完成的单个资产及安全错误摘要")
    public record Failure(
            @Schema(description = "资产 UUID。")
            UUID assetId,
            @Schema(description = "开始批处理时该资产的有效展示名称，用于定位失败项。")
            String assetName,
            @Schema(description = "该资产未能完成检查或同步的安全错误摘要。")
            String message
    ) {
    }
}
