package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetStorageKind;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload;
import cn.superhuang.data.scalpel.contract.execution.SparkJarExecutionPayload;
import cn.superhuang.data.scalpel.contract.execution.SparkStreamingJarExecutionPayload;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CanvasTaskRunManifest(
        int manifestVersion,
        Execution execution,
        Task task,
        MetadataSnapshot metadataSnapshot,
        List<RuntimeDataSource> runtimeDataSources,
        Streaming streaming,
        RuntimeFileStorage runtimeFileStorage,
        List<RuntimeFileInput> runtimeFileInputs,
        SnapshotSyncLimits snapshotSyncLimits,
        @JsonProperty("taskType") ExecutionTaskType executionTaskType,
        ModelQualityExecutionPayload modelQuality,
        SparkJarExecutionPayload sparkJarJob,
        SparkStreamingJarExecutionPayload streamingSparkJarJob
) {
    public static final int CURRENT_MANIFEST_VERSION = 21;

    public CanvasTaskRunManifest {
        runtimeDataSources = runtimeDataSources == null ? List.of() : List.copyOf(runtimeDataSources);
        runtimeFileInputs = runtimeFileInputs == null ? List.of() : List.copyOf(runtimeFileInputs);
        snapshotSyncLimits = snapshotSyncLimits == null ? SnapshotSyncLimits.defaults() : snapshotSyncLimits;
        executionTaskType = executionTaskType == null
                ? task != null && task.executionMode() == CanvasExecutionMode.STREAMING
                ? ExecutionTaskType.SPARK_STREAMING_CANVAS : ExecutionTaskType.SPARK_CANVAS
                : executionTaskType;
        if (executionTaskType == ExecutionTaskType.SPARK_JAR) {
            if (sparkJarJob == null || streamingSparkJarJob != null
                    || task != null || streaming != null || modelQuality != null) {
                throw new IllegalArgumentException("Spark JAR Manifest 载荷无效");
            }
        } else if (executionTaskType == ExecutionTaskType.SPARK_STREAMING_JAR) {
            if (streamingSparkJarJob == null || sparkJarJob != null
                    || task != null || streaming != null || modelQuality != null) {
                throw new IllegalArgumentException("Spark Streaming JAR Manifest 载荷无效");
            }
        } else if (sparkJarJob != null || streamingSparkJarJob != null) {
            throw new IllegalArgumentException("非 Spark JAR Manifest 不得包含 JAR 载荷");
        }
    }

    public CanvasTaskRunManifest(
            int manifestVersion,
            Execution execution,
            Task task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources,
                null, null, List.of(), SnapshotSyncLimits.defaults(), null, null, null, null);
    }

    public CanvasTaskRunManifest(
            int manifestVersion,
            Execution execution,
            Task task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources,
                streaming, null, List.of(), SnapshotSyncLimits.defaults(), null, null, null, null);
    }

    public CanvasTaskRunManifest(
            int manifestVersion,
            Execution execution,
            Task task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming,
            RuntimeFileStorage runtimeFileStorage,
            List<RuntimeFileInput> runtimeFileInputs
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources,
                streaming, runtimeFileStorage, runtimeFileInputs, SnapshotSyncLimits.defaults(), null, null, null, null);
    }

    public CanvasTaskRunManifest(
            int manifestVersion,
            Execution execution,
            Task task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming,
            RuntimeFileStorage runtimeFileStorage,
            List<RuntimeFileInput> runtimeFileInputs,
            SnapshotSyncLimits snapshotSyncLimits
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources, streaming,
                runtimeFileStorage, runtimeFileInputs, snapshotSyncLimits, null, null, null, null);
    }

    public CanvasTaskRunManifest(
            int manifestVersion, Execution execution, Task task, MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources, Streaming streaming,
            RuntimeFileStorage runtimeFileStorage, List<RuntimeFileInput> runtimeFileInputs,
            SnapshotSyncLimits snapshotSyncLimits, ExecutionTaskType executionTaskType,
            ModelQualityExecutionPayload modelQuality
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources, streaming,
                runtimeFileStorage, runtimeFileInputs, snapshotSyncLimits, executionTaskType,
                modelQuality, null, null);
    }

    public record SnapshotSyncLimits(
            int maxRowsPerSide,
            long maxEstimatedBytes,
            int lockTimeoutSeconds
    ) {
        public SnapshotSyncLimits {
            if (maxRowsPerSide < 1 || maxEstimatedBytes < 1 || lockTimeoutSeconds < 1) {
                throw new IllegalArgumentException("Snapshot Sync limits must be positive");
            }
        }

        public static SnapshotSyncLimits defaults() {
            return new SnapshotSyncLimits(100_000, 256L * 1024 * 1024, 30);
        }
    }

    public record Execution(
            UUID executionId,
            UUID runId,
            UUID taskId,
            int attempt,
            int definitionVersion,
            Instant createdAt,
            Instant deadlineAt,
            UUID deploymentId
    ) {
        public Execution(
                UUID executionId,
                UUID runId,
                UUID taskId,
                int attempt,
                int definitionVersion,
                Instant createdAt,
                Instant deadlineAt
        ) {
            this(executionId, runId, taskId, attempt, definitionVersion, createdAt, deadlineAt, null);
        }
    }

    public record Task(
            TaskType type,
            CanvasDefinition definition,
            CanvasExecutionMode executionMode
    ) {
        public Task(TaskType type, CanvasDefinition definition) {
            this(type, definition, CanvasExecutionMode.BATCH);
        }
    }

    public record RuntimeDataSource(
            UUID dataSourceId,
            ConnectionKind connectionKind,
            RuntimeDatabaseType databaseType,
            Set<DataSourcePurpose> purposes,
            RuntimeJdbcConnection connection,
            HttpApiContracts.RuntimeConnection httpApiConnection,
            List<HttpApiContracts.ResourceDefinition> apiResources,
            RuntimeKafkaConnection kafkaConnection,
            RuntimeS3Connection s3Connection,
            List<SpatialServiceResourceDefinition> spatialResources,
            RuntimeTdEngineTmqConnection tdEngineTmqConnection
    ) {
        public RuntimeDataSource {
            purposes = Set.copyOf(purposes);
            apiResources = apiResources == null ? List.of() : List.copyOf(apiResources);
            spatialResources = spatialResources == null ? List.of() : List.copyOf(spatialResources);
        }

        public RuntimeDataSource(
                UUID dataSourceId,
                ConnectionKind connectionKind,
                RuntimeDatabaseType databaseType,
                Set<DataSourcePurpose> purposes,
                RuntimeJdbcConnection connection,
                HttpApiContracts.RuntimeConnection httpApiConnection,
                List<HttpApiContracts.ResourceDefinition> apiResources,
                RuntimeKafkaConnection kafkaConnection,
                RuntimeS3Connection s3Connection,
                List<SpatialServiceResourceDefinition> spatialResources
        ) {
            this(dataSourceId, connectionKind, databaseType, purposes, connection,
                    httpApiConnection, apiResources, kafkaConnection, s3Connection,
                    spatialResources, null);
        }

        public RuntimeDataSource(
                UUID dataSourceId,
                ConnectionKind connectionKind,
                RuntimeDatabaseType databaseType,
                Set<DataSourcePurpose> purposes,
                RuntimeJdbcConnection connection,
                HttpApiContracts.RuntimeConnection httpApiConnection,
                List<HttpApiContracts.ResourceDefinition> apiResources
        ) {
            this(dataSourceId, connectionKind, databaseType, purposes, connection,
                    httpApiConnection, apiResources, null, null, List.of(), null);
        }

        public RuntimeDataSource(
                UUID dataSourceId,
                ConnectionKind connectionKind,
                RuntimeDatabaseType databaseType,
                Set<DataSourcePurpose> purposes,
                RuntimeJdbcConnection connection,
                HttpApiContracts.RuntimeConnection httpApiConnection,
                List<HttpApiContracts.ResourceDefinition> apiResources,
                RuntimeKafkaConnection kafkaConnection
        ) {
            this(dataSourceId, connectionKind, databaseType, purposes, connection,
                    httpApiConnection, apiResources, kafkaConnection, null, List.of(), null);
        }

        public RuntimeDataSource(
                UUID dataSourceId,
                ConnectionKind connectionKind,
                RuntimeDatabaseType databaseType,
                Set<DataSourcePurpose> purposes,
                RuntimeJdbcConnection connection,
                HttpApiContracts.RuntimeConnection httpApiConnection,
                List<HttpApiContracts.ResourceDefinition> apiResources,
                RuntimeKafkaConnection kafkaConnection,
                RuntimeS3Connection s3Connection
        ) {
            this(dataSourceId, connectionKind, databaseType, purposes, connection,
                    httpApiConnection, apiResources, kafkaConnection, s3Connection, List.of(), null);
        }
    }

    public enum RuntimeDatabaseType {
        POSTGRESQL,
        MYSQL,
        ORACLE,
        SQL_SERVER,
        CLICKHOUSE,
        DAMENG,
        OPENGAUSS,
        KINGBASE,
        TDENGINE_WEBSOCKET,
        TDENGINE_RESTFUL
    }

    public record RuntimeJdbcConnection(
            String driverClassName,
            String jdbcUrl,
            String catalogName,
            String schemaName,
            String username,
            String password,
            Map<String, String> properties
    ) {
        public RuntimeJdbcConnection {
            properties = Map.copyOf(properties);
        }
    }

    public record RuntimeKafkaConnection(
            String bootstrapServers,
            String securityProtocol,
            String saslMechanism,
            String username,
            String password
    ) {
    }

    public record RuntimeS3Connection(
            String endpoint,
            String region,
            String bucket,
            String rootPrefix,
            boolean pathStyleAccess,
            String accessKey,
            String secretKey
    ) {
    }

    public record RuntimeTdEngineTmqConnection(
            String bootstrapServers,
            String username,
            String password,
            boolean useSsl
    ) {
        @Override
        public String toString() {
            return "RuntimeTdEngineTmqConnection["
                    + "bootstrapServers=" + bootstrapServers
                    + ", usernameConfigured=" + (username != null && !username.isBlank())
                    + ", passwordConfigured=" + (password != null && !password.isBlank())
                    + ", useSsl=" + useSsl
                    + ']';
        }
    }

    public record RuntimeFileStorage(
            String endpoint,
            String region,
            String bucket,
            boolean pathStyleAccess,
            String accessKey,
            String secretKey
    ) {
    }

    public record RuntimeFileInput(
            UUID fileDatasetId,
            UUID fileDatasetTableId,
            String schemaFingerprint,
            RuntimeFileParsingOptions parsingOptions,
            List<RuntimeFileSource> sources
    ) {
        public RuntimeFileInput {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }

        public String objectKey() {
            return singleSource().objectKey();
        }

        private RuntimeFileSource singleSource() {
            if (sources.size() != 1) {
                throw new IllegalStateException("当前运行输入不是单一来源");
            }
            return sources.getFirst();
        }
    }

    public record RuntimeFileSource(
            UUID tableSourceId,
            UUID sourceFileId,
            FileDatasetFormat format,
            FileDatasetCompression compression,
            FileDatasetStorageKind storageKind,
            String objectKey,
            String materializedPrefix,
            String sourceKey
    ) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Csv.class, name = "CSV"),
            @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Text.class, name = "TEXT"),
            @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Json.class, name = "JSON"),
            @JsonSubTypes.Type(value = RuntimeFileParsingOptions.JsonLines.class, name = "JSON_LINES"),
            @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Spreadsheet.class, name = "SPREADSHEET"),
            @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Parquet.class, name = "PARQUET"),
            @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Avro.class, name = "AVRO"),
            @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Gdb.class, name = "GDB"),
            @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Shp.class, name = "SHP")
    })
    public sealed interface RuntimeFileParsingOptions permits
            RuntimeFileParsingOptions.Csv,
            RuntimeFileParsingOptions.Text,
            RuntimeFileParsingOptions.Json,
            RuntimeFileParsingOptions.JsonLines,
            RuntimeFileParsingOptions.Spreadsheet,
            RuntimeFileParsingOptions.Parquet,
            RuntimeFileParsingOptions.Avro,
            RuntimeFileParsingOptions.Gdb,
            RuntimeFileParsingOptions.Shp {

        record Csv(
                String charset,
                String fieldDelimiter,
                FileRecordDelimiter recordDelimiter,
                String quoteCharacter,
                String escapeCharacter,
                boolean firstRowHeader
        ) implements RuntimeFileParsingOptions {
        }

        record Text(String charset, FileRecordDelimiter recordDelimiter) implements RuntimeFileParsingOptions {
        }

        record Json(String charset, String rootPointer) implements RuntimeFileParsingOptions {
        }

        record JsonLines(String charset, FileRecordDelimiter recordDelimiter) implements RuntimeFileParsingOptions {
        }

        record Spreadsheet(int headerRowIndex, int dataStartRowIndex) implements RuntimeFileParsingOptions {
        }

        record Parquet() implements RuntimeFileParsingOptions {
        }

        record Avro() implements RuntimeFileParsingOptions {
        }

        record Gdb() implements RuntimeFileParsingOptions {
        }

        record Shp(String dbfCharsetOverride, String dbfFallbackCharset) implements RuntimeFileParsingOptions {
        }
    }

    public record Streaming(
            int triggerIntervalSeconds,
            String checkpointKeyPrefix,
            String sourceNodeId,
            String sourceSignature,
            String initialSourceOffset
    ) {
        public Streaming(int triggerIntervalSeconds, String checkpointKeyPrefix) {
            this(triggerIntervalSeconds, checkpointKeyPrefix, null, null, null);
        }
    }
}
