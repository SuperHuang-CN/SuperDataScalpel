package cn.superhuang.data.scalpel.business.service.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;
@Schema(description = "本地已确认的服务交付状态，不证明实时可用。")
public record DataServiceStatisticsResponse(
    @Schema(description = "采集时间，ISO UTC。") Instant collectedAt,
    @Schema(description = "当前ENABLED服务数。") long enabled,
    @Schema(description = "已启用范围内FAILED部署服务数。") long failed,
    @Schema(description = "已启用且非DEPLOYED、非FAILED的服务数，包括缺失部署记录。") long unconfirmed,
    @Schema(description = "有PUBLISHED绑定的现存服务去重数，不保证属于已启用范围。") long gatewayPublished,
    @Schema(description = "全部服务类型的启用及部署状态数量，包含零值。") List<TypeCount> types
) { @Schema(name = "DataServiceTypeStatistics", description = "服务类型交付计数。")
public record TypeCount(@Schema(description = "服务类型。") cn.superhuang.data.scalpel.contract.service.DataServiceType type,
 @Schema(description = "启用数量。") long enabled, @Schema(description = "已启用且部署FAILED数量。") long failed,
 @Schema(description = "已启用且部署未确认数量。") long unconfirmed) {} }

