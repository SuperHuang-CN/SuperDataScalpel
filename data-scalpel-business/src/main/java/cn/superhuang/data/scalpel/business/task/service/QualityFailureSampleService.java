package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParseSource;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingConfiguration;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.ParquetFileDatasetParser;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.web.response.QualityFailureSampleResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Authenticated access to immutable per-rule quality sample artifacts. */
@Service
public class QualityFailureSampleService {
    private static final int MAXIMUM_SAMPLE_BYTES = 20 * 1024 * 1024;

    private final TaskRunRepository runRepository;
    private final ObjectProvider<TaskRunArtifactStorage> storageProvider;
    private final ParquetFileDatasetParser parquetParser;
    private final ObjectMapper objectMapper;

    public QualityFailureSampleService(
            TaskRunRepository runRepository,
            ObjectProvider<TaskRunArtifactStorage> storageProvider,
            ParquetFileDatasetParser parquetParser,
            ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.storageProvider = storageProvider;
        this.parquetParser = parquetParser;
        this.objectMapper = objectMapper;
    }

    public QualityFailureSampleResponse preview(UUID runId, UUID ruleId) {
        SampleReference reference = reference(runId, ruleId);
        byte[] content = readAndVerify(reference);
        Path temporary = null;
        try {
            temporary = Files.createTempFile("data-scalpel-quality-sample-", ".parquet");
            Files.write(temporary, content);
            FileDatasetParser.ParseResult parsed = parquetParser.parse(
                    new FileDatasetParseSource.LocalFile(temporary),
                    new FileDatasetParsingConfiguration.Parquet(),
                    Math.toIntExact(reference.sampledRows()));
            if (parsed.rowCount() != reference.sampledRows() || parsed.rows().size() != reference.sampledRows()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "质检样本行数与结果描述不一致");
            }
            Map<String, QualityFailureSampleResponse.Column> metadata = new LinkedHashMap<>();
            reference.columns().forEach(column -> metadata.put(column.code(), column));
            if (!parsed.fields().stream().map(FileDatasetParser.Field::name).toList()
                    .equals(reference.columns().stream().map(QualityFailureSampleResponse.Column::code).toList())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "质检样本字段与结果描述不一致");
            }
            List<Map<String, Object>> rows = parsed.rows().stream().map(row -> convert(row, metadata)).toList();
            return new QualityFailureSampleResponse(
                    ruleId, reference.ruleName(), reference.sampledRows(), reference.violationRows(),
                    reference.truncated(), reference.rowLocatable(), reference.columns(), rows);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "无法解析质检失败样本", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (Exception ignored) { }
            }
        }
    }

    public QualitySampleDownload download(UUID runId, UUID ruleId) {
        SampleReference reference = reference(runId, ruleId);
        return new QualitySampleDownload(
                readAndVerify(reference), safeFileName(reference.ruleName(), ruleId));
    }

    private SampleReference reference(UUID runId, UUID ruleId) {
        TaskRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务运行不存在"));
        if (run.getTaskType() != TaskType.SPARK_MODEL_QUALITY || run.getStatus() != TaskRunStatus.SUCCESS
                || run.getAttempt() == null || run.getResultObjectKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务运行没有可读取的质检失败样本");
        }
        TaskRunArtifactStorage storage = requireStorage();
        byte[] result = storage.readIfPresent(run.getResultObjectKey(), 5 * 1024 * 1024)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "质检结果尚未生成"));
        try {
            JsonNode root = objectMapper.readTree(result);
            if (root.path("schemaVersion").asLong() != 5
                    || !"SPARK_MODEL_QUALITY".equals(root.path("taskType").asText())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "当前质检结果不支持失败样本");
            }
            JsonNode found = null;
            for (JsonNode rule : root.path("qualityResult").path("ruleResults")) {
                if (ruleId.toString().equals(rule.path("ruleId").asText())) {
                    found = rule;
                    break;
                }
            }
            if (found == null || !"FAILED".equals(found.path("state").asText())
                    || !"AVAILABLE".equals(found.path("sample").path("status").asText())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前规则没有可用失败样本");
            }
            JsonNode sample = found.path("sample");
            List<QualityFailureSampleResponse.Column> columns = new ArrayList<>();
            for (JsonNode column : sample.path("columns")) {
                columns.add(new QualityFailureSampleResponse.Column(
                        optionalUuid(column.path("fieldId")), requiredText(column, "code"),
                        requiredText(column, "name"),
                        objectMapper.treeToValue(column.path("type"), PlatformTypeDefinition.class),
                        column.path("primaryKey").asBoolean(), column.path("diagnostic").asBoolean()));
            }
            long sampledRows = sample.path("sampledRows").asLong(-1);
            long violationRows = sample.path("violationRows").asLong(-1);
            long sizeBytes = sample.path("sizeBytes").asLong(-1);
            String digest = sample.path("sha256").asText();
            if (sampledRows < 1 || sampledRows > 1000 || violationRows < sampledRows
                    || sizeBytes < 8 || sizeBytes > MAXIMUM_SAMPLE_BYTES
                    || !digest.matches("[0-9a-f]{64}") || columns.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "质检样本描述无效");
            }
            String objectKey = "task-runs/%s/attempts/%d/quality/samples/%s.parquet".formatted(
                    run.getExecutionRunId(), run.getAttempt(), ruleId);
            return new SampleReference(
                    objectKey, requiredText(found, "ruleName"), sampledRows, violationRows,
                    sample.path("truncated").asBoolean(), sizeBytes, digest,
                    sample.path("rowLocatable").asBoolean(), columns);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "质检结果样本描述无效", exception);
        }
    }

    private byte[] readAndVerify(SampleReference reference) {
        try {
            byte[] content = requireStorage().readIfPresent(reference.objectKey(), MAXIMUM_SAMPLE_BYTES)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "质检失败样本不存在"));
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            if (content.length != reference.sizeBytes()
                    || !MessageDigest.isEqual(digest.getBytes(StandardCharsets.US_ASCII),
                    reference.sha256().getBytes(StandardCharsets.US_ASCII))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "质检失败样本完整性校验失败");
            }
            return content;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储当前不可用", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "质检失败样本摘要校验失败", exception);
        }
    }

    private TaskRunArtifactStorage requireStorage() {
        TaskRunArtifactStorage storage = storageProvider.getIfAvailable();
        if (storage == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "任务制品存储尚未配置");
        return storage;
    }

    private static Map<String, Object> convert(
            Map<String, Object> source,
            Map<String, QualityFailureSampleResponse.Column> columns
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        columns.forEach((code, column) -> result.put(code, jsonValue(source.get(code), column.type().type())));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Object jsonValue(Object value, PlatformDataType type) {
        if (value == null) return null;
        return switch (type) {
            case LONG, DECIMAL -> String.valueOf(value);
            case DATE -> value instanceof LocalDate date ? date.toString() : String.valueOf(value);
            case TIMESTAMP -> value instanceof Instant instant ? instant.toString() : String.valueOf(value);
            case TIMESTAMP_NTZ -> value instanceof LocalDateTime dateTime ? dateTime.toString() : String.valueOf(value);
            case BYTE, SHORT, INTEGER, FLOAT, DOUBLE, BOOLEAN -> value;
            default -> String.valueOf(value);
        };
    }

    private static UUID optionalUuid(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() ? null : UUID.fromString(value.asText());
    }

    private static String requiredText(JsonNode parent, String field) {
        String value = parent.path(field).asText();
        if (value == null || value.isBlank()) throw new IllegalArgumentException("字段缺失：" + field);
        return value;
    }

    private static String safeFileName(String ruleName, UUID ruleId) {
        String normalized = ruleName.replaceAll("[\\r\\n\\t/\\\\\";]", "_").trim();
        if (normalized.isBlank()) normalized = "quality-rule-" + ruleId;
        if (normalized.length() > 80) normalized = normalized.substring(0, 80);
        return normalized + "-失败样本.parquet";
    }

    public record QualitySampleDownload(byte[] content, String fileName) {
        public QualitySampleDownload { content = content.clone(); }
        @Override public byte[] content() { return content.clone(); }
    }

    private record SampleReference(
            String objectKey, String ruleName, long sampledRows, long violationRows,
            boolean truncated, long sizeBytes, String sha256, boolean rowLocatable,
            List<QualityFailureSampleResponse.Column> columns
    ) {
    }
}
