package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitSampleMode;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitStage;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The only user-visible development kit for one batch Spark JAR task. */
public record SparkJarDevelopmentKitResponse(
        UUID taskId,
        int definitionVersion,
        Configuration configuration,
        Generation generation,
        Artifact artifact
) {
    public record Configuration(List<InputSample> samples, List<JdbcTableSample> jdbcTables) {
        public Configuration {
            samples = List.copyOf(samples);
            jdbcTables = List.copyOf(jdbcTables);
        }
    }

    public record InputSample(String bindingName, SparkJarDevelopmentKitSampleMode mode,
                              Integer rowCount, BigDecimal percentage) {}

    public record JdbcTableSample(String bindingName, String catalog, String schema, String table,
                                  SparkJarDevelopmentKitSampleMode mode,
                                  Integer rowCount, BigDecimal percentage) {}

    public record Generation(UUID id, int definitionVersion, SparkJarDevelopmentKitStatus status,
                             SparkJarDevelopmentKitStage stage, int progressPercent, String currentModel,
                             int attemptCount, String errorCode, String errorMessage,
                             Instant createdAt, Instant startedAt, Instant completedAt) {}

    public record Artifact(long sizeBytes, String sha256, Instant generatedAt,
                           boolean matchesSavedConfiguration) {}
}
