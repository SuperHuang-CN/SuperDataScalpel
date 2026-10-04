package cn.superhuang.data.scalpel.business.asset.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;
@Schema(description = "发布资产与最近检查的同步状态，不触发检查。")
public record AssetStatisticsResponse(
    @Schema(description = "采集时间，ISO UTC。") Instant collectedAt,
    @Schema(description = "已发布资产数，不与模型数相加。") long published,
    @Schema(description = "已发布且OUTDATED的资产数。") long outdated,
    @Schema(description = "已发布且来源不可用、缺失或检查失败的资产数。") long sourceIssues
) {  }
