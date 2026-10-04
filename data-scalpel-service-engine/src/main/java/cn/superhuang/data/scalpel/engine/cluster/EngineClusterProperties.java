package cn.superhuang.data.scalpel.engine.cluster;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** Small, bounded control-plane polling; never used to query the database per business request. */
@Validated
@ConfigurationProperties("data-scalpel.engine.cluster")
public record EngineClusterProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("1000") @Min(200) @Max(30000) long pollIntervalMs,
        @DefaultValue("10000") @Min(1000) @Max(120000) long maxStaleMs
) {
    public EngineClusterProperties {
        if (maxStaleMs <= pollIntervalMs * 2) throw new IllegalArgumentException("Engine 配置失联保护时间必须大于两个轮询周期");
    }
}
