package cn.superhuang.data.scalpel.business.service.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineAccessPolicyStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Admin 期望的服务引擎网络访问策略及引擎实际应用进度。")

public record ServiceEngineAccessPolicyResponse(
        @Schema(description = "应用该访问策略的服务引擎 UUID。")
        UUID engineId,
        @Schema(description = "允许访问该 Engine 已部署业务服务的 CIDR 网段列表；保存策略时至少一个。未配置策略的虚拟响应为空列表，Engine 此时默认拒绝业务服务访问。")
        List<String> allowCidrs,
        @Schema(description = "明确拒绝访问业务服务的 CIDR 网段列表；拒绝规则优先于允许规则。管理接口不受该业务访问策略过滤。")
        List<String> denyCidrs,
        @Schema(description = "Admin 中最新保存的访问策略修订号；未配置时为 0，首次保存为 1，此后每次更新请求均递增，即使规范化后的规则内容未变化。")
        long desiredRevision,
        @Schema(description = "服务引擎最近确认应用的策略修订号；从未成功应用时为 0，小于 desiredRevision 表示当前 Admin 期望尚未同步。")
        long appliedRevision,
        @Schema(description = "策略同步状态：NOT_CONFIGURED 未配置，PENDING 待应用，READY 已同步，OUTDATED 引擎落后，FAILED 同步失败。")
        ServiceEngineAccessPolicyStatus status,
        @Schema(description = "最近一次应用失败或因引擎地址、管理身份变化而过期的安全说明；READY 或未配置时为空。")
        String lastError,
        @Schema(description = "最近一次策略成功应用到 Engine 的时间，ISO-8601 UTC 时间戳；从未成功应用时为空，后续失败或过期不会清除此历史时间。")
        Instant appliedAt,
        @Schema(description = "策略记录最后更新时间，ISO-8601 UTC 时间戳；未配置策略的虚拟响应为空。")
        Instant updatedAt
) {
}
