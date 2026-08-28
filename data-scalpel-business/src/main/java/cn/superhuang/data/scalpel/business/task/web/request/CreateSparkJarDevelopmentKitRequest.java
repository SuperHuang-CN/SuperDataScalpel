package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitSampleMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CreateSparkJarDevelopmentKitRequest(
        @Min(1) int definitionVersion,
        List<@Valid InputSample> samples,
        List<@Valid JdbcTableSample> jdbcTables
) {
    public CreateSparkJarDevelopmentKitRequest {
        samples = samples == null ? List.of() : List.copyOf(samples);
        jdbcTables = jdbcTables == null ? List.of() : List.copyOf(jdbcTables);
    }

    public record InputSample(
            @NotBlank @Size(max = 100) String bindingName,
            @NotNull SparkJarDevelopmentKitSampleMode mode,
            @Min(1) @Max(1_000_000) Integer rowCount,
            @DecimalMin("0.01") @DecimalMax("100") BigDecimal percentage
    ) {}

    /** A physical JDBC table included only in this generated local development kit. */
    public record JdbcTableSample(
            @NotBlank @Size(max = 100) String bindingName,
            @Size(max = 255) String catalog,
            @Size(max = 255) String schema,
            @NotBlank @Size(max = 255) String table,
            @NotNull SparkJarDevelopmentKitSampleMode mode,
            @Min(1) @Max(1_000_000) Integer rowCount,
            @DecimalMin("0.01") @DecimalMax("100") BigDecimal percentage
    ) {}
}
