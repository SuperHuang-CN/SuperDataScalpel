package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Desired Engine data-plane source-address policy sent by the Admin control plane. */
public record EngineAccessPolicyApplyRequest(
        @JsonPropertyDescription("目标 Service Engine 的稳定唯一编码。")
        @NotBlank String engineCode,
        @JsonPropertyDescription("Admin 期望应用的策略修订号，必须大于 0。高于 Engine 当前修订时应用；等于当前修订且内容相同视为幂等成功，内容不同返回冲突；低于当前修订返回冲突。")
        long revision,
        @JsonPropertyDescription("允许访问该引擎 /open-api/v1/** 业务服务的规范化 CIDR 列表，至少一个；不控制 Engine 管理接口。")
        @NotNull @NotEmpty List<@NotBlank String> allowCidrs,
        @JsonPropertyDescription("显式拒绝访问的规范化 CIDR 列表；优先于允许规则。")
        @NotNull List<@NotBlank String> denyCidrs
) {
    public EngineAccessPolicyApplyRequest {
        allowCidrs = List.copyOf(allowCidrs);
        denyCidrs = List.copyOf(denyCidrs);
    }
}
