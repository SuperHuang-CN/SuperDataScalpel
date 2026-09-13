package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Request sent by the Admin control plane to create or replace an Engine deployment. */
public record ServiceDeploymentRequest(
        @JsonPropertyDescription("数据服务 UUID。")
        @NotNull UUID serviceId,
        @JsonPropertyDescription("数据服务稳定唯一编码，用于引擎侧部署标识。")
        @NotBlank String serviceCode,
        @JsonPropertyDescription("服务对外路由路径。")
        @NotBlank String routePath,
        @JsonPropertyDescription("规范化服务定义的 SHA-256 摘要，用于识别部署内容。")
        @NotBlank String definitionDigest,
        @JsonPropertyDescription("规范化服务定义快照；type 与且仅与一个 standardDefinition、sqlDefinition、scriptDefinition 或 spatialDefinition 匹配。")
        @NotNull @Valid ServiceDefinitionSnapshot definition,
        @JsonPropertyDescription("数据源 UUID。")
        @NotNull UUID dataSourceId
) {
}
