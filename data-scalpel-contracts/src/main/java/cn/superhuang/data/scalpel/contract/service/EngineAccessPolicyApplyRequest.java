package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Desired Engine data-plane source-address policy sent by the Admin control plane. */
public record EngineAccessPolicyApplyRequest(
        @NotBlank String engineCode,
        long revision,
        @NotNull @NotEmpty List<@NotBlank String> allowCidrs,
        @NotNull List<@NotBlank String> denyCidrs
) {
    public EngineAccessPolicyApplyRequest {
        allowCidrs = List.copyOf(allowCidrs);
        denyCidrs = List.copyOf(denyCidrs);
    }
}
