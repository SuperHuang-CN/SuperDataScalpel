package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.UUID;

/** Deployment acknowledgement emitted by an Engine. */
public record ServiceDeploymentResponse(
        @JsonPropertyDescription("数据服务 UUID。")
        UUID serviceId,
        @JsonPropertyDescription("引擎部署结果：DEPLOYED 已创建或替换部署，REMOVED 已移除部署。")
        EngineDeploymentStatus status,
        @JsonPropertyDescription("Service Engine 对本次创建、替换或移除部署结果的可读说明。")
        String message
) {
}
