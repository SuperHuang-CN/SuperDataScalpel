package cn.superhuang.data.scalpel.engine.query;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "data-scalpel.engine.query")
public record EngineQueryProperties(
        @Min(1) @Max(1000) int defaultPageSize,
        @Min(1) @Max(1000) int maximumPageSize,
        @Min(1) @Max(100) int maximumFilterCount,
        @Min(1) @Max(20) int maximumFilterDepth,
        @Min(1) @Max(10000) int maximumInValues,
        @Min(1) int maximumOffset,
        @Min(1) @Max(300) int timeoutSeconds
) {

    public EngineQueryProperties {
        if (defaultPageSize > maximumPageSize) {
            throw new IllegalArgumentException("默认分页大小不能大于最大分页大小");
        }
    }
}
