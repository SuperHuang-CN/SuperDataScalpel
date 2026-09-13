package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Result of an Engine data source registration request. */
public record EngineDataSourceRegistrationResponse(
        @JsonPropertyDescription("目标 Service Engine 的稳定唯一编码。")
        @NotBlank String engineCode,
        @JsonPropertyDescription("数据源 UUID。")
        @NotNull UUID dataSourceId,
        @JsonPropertyDescription("引擎侧数据源结果：READY 已注册可用，REMOVED 已移除。")
        @NotNull EngineDataSourceStatus status,
        @JsonPropertyDescription("Service Engine 对本次数据源注册或移除结果的可读说明。")
        @NotBlank String message
) {
}
