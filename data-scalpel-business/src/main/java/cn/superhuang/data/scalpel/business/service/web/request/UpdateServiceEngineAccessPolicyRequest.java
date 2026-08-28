package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateServiceEngineAccessPolicyRequest(
        @NotNull @NotEmpty List<@NotBlank String> allowCidrs,
        @NotNull List<@NotBlank String> denyCidrs
) {
}
