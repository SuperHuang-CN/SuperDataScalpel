package cn.superhuang.data.scalpel.business.service.accesslog.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.accesslog.domain.GatewayAccessIdentityResolutionStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "一条已接收并完成本地身份映射的 API 网关访问事件。")

public record GatewayAccessLogResponse(
        @Schema(description = "本地网关访问日志记录 UUID。")
        UUID id,
        @Schema(description = "来源网关为该日志事件提供的稳定 UUID；旧协议或来源未提供时为空。")
        UUID eventId,
        @Schema(description = "来源访问事件所使用的消息 Schema 版本。")
        String schemaVersion,
        @Schema(description = "网关提供方。")
        GatewayProvider gatewayProvider,
        @Schema(description = "事件在来源系统中的发生时间。")
        Instant occurredAt,
        @Schema(description = "外部系统中实际观测时间。")
        Instant observedAt,
        @Schema(description = "平台接收事件的时间。")
        Instant receivedAt,
        @Schema(description = "解析到的本地数据服务 UUID；服务无法匹配时为空并查看 identityResolutionStatus。")
        UUID dataServiceId,
        @Schema(description = "数据服务稳定编码。")
        String dataServiceCode,
        @Schema(description = "数据服务名称。")
        String dataServiceName,
        @Schema(description = "日志中记录的网关侧服务对象标识；请求未匹配服务时为空。")
        String gatewayServiceId,
        @Schema(description = "网关中的服务名称。")
        String gatewayServiceName,
        @Schema(description = "日志中记录的网关侧路由对象标识；请求未匹配路由时为空。")
        String gatewayRouteId,
        @Schema(description = "网关中的路由名称。")
        String gatewayRouteName,
        @Schema(description = "解析到的本地 API 消费者 UUID；匿名或无法匹配时为空。")
        UUID consumerId,
        @Schema(description = "日志中记录的网关侧消费者对象标识；匿名请求时为空。")
        String gatewayConsumerId,
        @Schema(description = "API 调用方稳定编码。")
        String consumerCode,
        @Schema(description = "API 调用方名称。")
        String consumerName,
        @Schema(description = "网关侧用于鉴权的凭证对象标识；匿名或来源未提供时为空。")
        String gatewayCredentialExternalId,
        @Schema(description = "网关为本次 HTTP 请求分配的追踪标识；来源未提供时为空。")
        String gatewayRequestId,
        @Schema(description = "HTTP 请求方法。")
        String requestMethod,
        @Schema(description = "网关观测到的 HTTP 请求路径，不包含请求体。")
        String requestPath,
        @Schema(description = "网关最终返回给客户端的 HTTP 状态码。")
        int responseStatus,
        @Schema(description = "上游服务返回的状态或网关来源提供的状态文本；请求未到达上游时为空。")
        String upstreamStatus,
        @Schema(description = "网关观测到的请求大小，单位字节；来源未提供时为空。")
        Long requestSizeBytes,
        @Schema(description = "网关观测到的响应大小，单位字节；来源未提供时为空。")
        Long responseSizeBytes,
        @Schema(description = "网关从接收请求到完成响应的总延迟，单位毫秒；来源未提供时为空。")
        Long requestLatencyMs,
        @Schema(description = "Kong 网关自身处理耗时，单位毫秒；非 Kong 来源或未提供时为空。")
        Long kongLatencyMs,
        @Schema(description = "网关转发请求到上游并等待响应的耗时，单位毫秒；来源未提供时为空。")
        Long proxyLatencyMs,
        @Schema(description = "网关接收客户端请求数据的耗时，单位毫秒；来源未提供时为空。")
        Long receiveLatencyMs,
        @Schema(description = "客户端 IP 地址；受代理可信配置影响。")
        String clientIp,
        @Schema(description = "本地身份映射结果：RESOLVED、ANONYMOUS、消费者未解析、服务未解析或身份不一致。")
        GatewayAccessIdentityResolutionStatus identityResolutionStatus,
        @Schema(description = "请求是否在到达上游前被网关鉴权、限流或路由策略拒绝。")
        boolean gatewayRejected,
        @Schema(description = "响应是否被归类为网关自身处理错误。")
        boolean gatewayError,
        @Schema(description = "响应是否被归类为数据服务上游错误。")
        boolean upstreamError,
        @Schema(description = "承载该访问事件的 Kafka Topic。")
        String kafkaTopic,
        @Schema(description = "该事件所在的 Kafka 分区编号。")
        int kafkaPartition,
        @Schema(description = "该事件在 Kafka 分区内的 Offset。")
        long kafkaOffset
) {
}
