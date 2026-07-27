package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition;
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

public record CanvasTaskRunManifest(
        int manifestVersion,
        Execution execution,
        Task task,
        MetadataSnapshot metadataSnapshot,
        List<RuntimeDataSource> runtimeDataSources,
        Streaming streaming,
        RuntimeFileStorage runtimeFileStorage,
        List<RuntimeFileInput> runtimeFileInputs
) {
    public static final int CURRENT_MANIFEST_VERSION = 6;

    public CanvasTaskRunManifest {
        runtimeDataSources = runtimeDataSources == null ? List.of() : List.copyOf(runtimeDataSources);
        runtimeFileInputs = runtimeFileInputs == null ? List.of() : List.copyOf(runtimeFileInputs);
    }

    public CanvasTaskRunManifest(
            int manifestVersion,
            Execution execution,
            Task task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources, null, null, List.of());
    }

    public CanvasTaskRunManifest(
            int manifestVersion,
            Execution execution,
            Task task,
            MetadataSnapshot metadataSnapshot,
            List<RuntimeDataSource> runtimeDataSources,
            Streaming streaming
    ) {
        this(manifestVersion, execution, task, metadataSnapshot, runtimeDataSources, streaming, null, List.of());
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
            RuntimeKafkaConnection kafkaConnection
    ) {
        public RuntimeDataSource {
            purposes = Set.copyOf(purposes);
            apiResources = apiResources == null ? List.of() : List.copyOf(apiResources);
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
                    httpApiConnection, apiResources, null);
        }
    }

    public enum RuntimeDatabaseType {
        POSTGRESQL,
        MYSQL
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
            String checkpointKeyPrefix
    ) {
    }
}
