package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Idempotent request to remove a previously deployed service route. */
public record ServiceUndeploymentRequest(
        @JsonPropertyDescription("数据服务 UUID。")
        @NotNull UUID serviceId
) {
}
