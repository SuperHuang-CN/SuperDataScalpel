package cn.superhuang.data.scalpel.business.service.accesslog.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
@Schema(description="网关完整小时内的使用摘要；仅覆盖已接收且纳入小时汇总的日志，直连调用不包含。")
public record GatewayAccessUsageResponse(
 @Schema(description="快照采集时间，ISO UTC。") Instant collectedAt,
 @Schema(description="起点，包含，UTC完整小时。") Instant from,
 @Schema(description="终点，不包含，UTC完整小时，不代表日志已全部到达。") Instant to,
 @Schema(description="日志接收配置开关，不证明采集链路健康。") boolean collectionEnabled,
 @Schema(description="窗口内是否存在小时统计记录；false表示没有统计样本，不能证明没有调用。") boolean hasSamples,
 @Schema(description="窗口内至少一次2xx且已识别服务ID的不同服务数，可能含历史已删除服务。") long activeServices,
 @Schema(description="窗口内至少一次2xx且已识别Consumer ID的不同调用方数，排除匿名。") long activeConsumers,
 @Schema(description="已识别服务且身份无冲突的小时汇总中2xx次数；包含匿名及未识别调用方，不包含未识别服务的日志。") long successCount,
 @Schema(description="已识别服务且身份无冲突的小时汇总中5xx次数，不与其他错误分类相加。") long serverErrorCount,
 @Schema(description="窗口内最近存在统计记录的小时起点；无记录为空，不证明采集完整。") Instant latestRecordedHour
) {}
