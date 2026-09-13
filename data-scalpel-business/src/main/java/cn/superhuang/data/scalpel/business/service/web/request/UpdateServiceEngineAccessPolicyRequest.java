package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "整体替换 DataScalpel Service Engine 业务服务入口的允许和拒绝 CIDR 网段；不控制 Engine 管理接口。保存后立即同步，失败仍保留新的期望策略。")

public record UpdateServiceEngineAccessPolicyRequest(
        @Schema(description = "允许访问 Engine 已部署业务服务的 IP 或 CIDR 列表，不得为空。单个 IP 自动补 /32 或 /128，主机位会归零，重复规则去重并保留首次顺序；只接受字面 IP，不解析主机名。")
        @NotNull @NotEmpty List<@NotBlank String> allowCidrs,
        @Schema(description = "即使位于允许范围内也明确拒绝的 IP 或 CIDR 列表，规范化规则同 allowCidrs；拒绝优先，空列表表示没有额外拒绝项。")
        @NotNull List<@NotBlank String> denyCidrs
) {
}
