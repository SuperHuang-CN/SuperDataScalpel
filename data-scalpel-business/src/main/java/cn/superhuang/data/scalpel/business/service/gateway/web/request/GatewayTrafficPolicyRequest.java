package cn.superhuang.data.scalpel.business.service.gateway.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

@Schema(description = "服务网关单节点保护策略；数值零表示不限制。只支持自研网关，不修改服务定义或发布状态。")
public record GatewayTrafficPolicyRequest(
        @Schema(description = "每节点服务每秒令牌补充数，0～1000000，桶容量相同，允许一秒突发。") @Min(0) @Max(1_000_000) int requestsPerSecond,
        @Schema(description = "每节点每消费者在该服务上的每秒令牌数，0～1000000；匿名调用只受服务总限流。") @Min(0) @Max(1_000_000) int consumerRequestsPerSecond,
        @Schema(description = "每节点服务最大并发请求数，0～100000；响应结束、失败或取消时释放。") @Min(0) @Max(100_000) int maxConcurrentRequests,
        @Schema(description = "请求体最大字节数，0～1073741824；分块传输逐块检查，超限返回 413。") @Min(0) @Max(1_073_741_824L) long maxRequestBytes,
        @Schema(description = "TCP 来源 IP 白名单，最多 64 条非空 IPv4/IPv6 或 CIDR；空列表不限制。不信任转发头。") @Size(max = 64) List<@NotBlank @Size(max = 64) String> allowedCidrs,
        @Schema(description = "TCP 来源 IP 黑名单，最多 64 条非空 IPv4/IPv6 或 CIDR；优先于白名单。") @Size(max = 64) List<@NotBlank @Size(max = 64) String> deniedCidrs
) {}
