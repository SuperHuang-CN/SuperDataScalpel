package cn.superhuang.data.scalpel.business.service.gateway.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "自研网关持久化策略及所连接节点的配置传播状态。")
public record GatewayTrafficPolicyResponse(
        @Schema(description = "当前已保存的策略。") Values policy,
        @Schema(description = "网关最新配置修订；保存后需等待节点加载。") long targetRevision,
        @Schema(description = "本次连接节点已加载修订；小于目标表示尚未生效，其他节点在网关运行页查看。") long loadedRevision,
        @Schema(description = "限流范围，固定 NODE，表示每个节点独立，不是集群配额。") String scope
) {
    @Schema(description = "保护策略数值；零表示不限制，IP 空列表表示无对应规则。")
    public record Values(
            @Schema(description = "单节点服务每秒请求令牌数。") int requestsPerSecond,
            @Schema(description = "单节点每消费者对此服务的每秒请求令牌数。") int consumerRequestsPerSecond,
            @Schema(description = "单节点服务并发上限。") int maxConcurrentRequests,
            @Schema(description = "请求体字节上限。") long maxRequestBytes,
            @Schema(description = "允许的 TCP 来源 IP/CIDR 列表。") List<String> allowedCidrs,
            @Schema(description = "拒绝的 TCP 来源 IP/CIDR 列表，优先匹配。") List<String> deniedCidrs
    ) {}
}
