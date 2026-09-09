package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/** Collects bounded trial output after Spark has materialized a write preview. */
final class SparkJarTrialPreviewCollector {
    private static final int MAXIMUM_PREVIEW_BYTES = 4 * 1024 * 1024 - 4096;
    private static final int MAXIMUM_ROW_BYTES = 256 * 1024;

    private final boolean enabled;
    private final boolean streaming;
    private final Consumer<SparkJarTrialPreview> publisher;
    private final ObjectMapper objectMapper;
    private final List<SparkJarTrialPreview.WritePreview> batchWrites = new ArrayList<>();
    private final LinkedHashMap<OutputKey, StreamingOutput> streamingOutputs = new LinkedHashMap<>();
    private final LinkedHashSet<String> warnings = new LinkedHashSet<>();

    SparkJarTrialPreviewCollector(
            boolean enabled,
            boolean streaming,
            Consumer<SparkJarTrialPreview> publisher,
            ObjectMapper objectMapper
    ) {
        this.enabled = enabled;
        this.streaming = streaming;
        this.publisher = publisher == null ? ignored -> { } : publisher;
        this.objectMapper = objectMapper;
    }

    void capture(
            SparkJarTrialPreview.ResourceKind kind,
            String bindingName,
            String target,
            String mode,
            Dataset<Row> dataset
    ) {
        if (!enabled) return;
        List<String> collected = dataset.limit(SparkJarTrialPreview.MAX_ROWS_PER_WRITE + 1)
                .toJSON().collectAsList();
        String schemaJson = dataset.schema().json();
        boolean truncated = collected.size() > SparkJarTrialPreview.MAX_ROWS_PER_WRITE;
        boolean oversizedRow = false;
        List<String> rows = new ArrayList<>();
        for (String row : collected.subList(0, Math.min(collected.size(), SparkJarTrialPreview.MAX_ROWS_PER_WRITE))) {
            if (bytes(row) > MAXIMUM_ROW_BYTES) {
                truncated = true;
                oversizedRow = true;
            } else {
                rows.add(row);
            }
        }
        SparkJarTrialPreview before;
        SparkJarTrialPreview snapshot;
        synchronized (this) {
            before = snapshotLocked();
            if (oversizedRow) addWarning("单行输出超过 256 KiB，已从试运行预览中省略");
            if (streaming) mergeStreaming(kind, bindingName, target, mode, schemaJson, rows, truncated);
            else appendBatch(kind, bindingName, target, mode, schemaJson, rows, truncated);
            trimToMaximumBytes();
            snapshot = snapshotLocked();
        }
        if (!snapshot.equals(before)) publisher.accept(snapshot);
    }

    synchronized SparkJarTrialPreview snapshot() {
        return enabled ? snapshotLocked() : null;
    }

    private void appendBatch(
            SparkJarTrialPreview.ResourceKind kind,
            String bindingName,
            String target,
            String mode,
            String schemaJson,
            List<String> rows,
            boolean truncated
    ) {
        if (batchWrites.size() >= SparkJarTrialPreview.MAX_WRITES) {
            addWarning("输出写入超过 20 次，仅保留前 20 次预览");
            return;
        }
        batchWrites.add(new SparkJarTrialPreview.WritePreview(
                batchWrites.size() + 1, kind, bindingName, target, mode,
                schemaJson, rows, truncated));
    }

    private void mergeStreaming(
            SparkJarTrialPreview.ResourceKind kind,
            String bindingName,
            String target,
            String mode,
            String schemaJson,
            List<String> rows,
            boolean truncated
    ) {
        OutputKey key = new OutputKey(kind, bindingName, target);
        StreamingOutput output = streamingOutputs.get(key);
        if (output == null) {
            if (streamingOutputs.size() >= SparkJarTrialPreview.MAX_WRITES) {
                addWarning("实时输出目标超过 20 个，其他目标未加入预览");
                return;
            }
            output = new StreamingOutput(streamingOutputs.size() + 1, kind, bindingName, target,
                    mode, schemaJson);
            streamingOutputs.put(key, output);
        } else if (!output.schemaJson.equals(schemaJson)) {
            output.rows.clear();
            output.schemaJson = schemaJson;
            output.mode = mode;
            output.truncated = true;
            addWarning("输出 " + bindingName + " 的 Schema 已变化，旧样例已清空");
        }
        output.mode = mode;
        output.rows.addAll(rows);
        if (output.rows.size() > SparkJarTrialPreview.MAX_ROWS_PER_WRITE) {
            output.rows.subList(0, output.rows.size() - SparkJarTrialPreview.MAX_ROWS_PER_WRITE).clear();
            output.truncated = true;
        }
        output.truncated |= truncated;
    }

    private void trimToMaximumBytes() {
        while (serializedBytes() > MAXIMUM_PREVIEW_BYTES && removeOldestRows()) {
            addWarning("试运行预览接近 4 MiB 上限，已移除最早样例");
        }
        while (serializedBytes() > MAXIMUM_PREVIEW_BYTES && removeNewestOutput()) {
            addWarning("试运行预览接近 4 MiB 上限，已省略过大的输出结构");
        }
    }

    private boolean removeNewestOutput() {
        if (streaming) {
            if (streamingOutputs.isEmpty()) return false;
            OutputKey newest = null;
            for (OutputKey key : streamingOutputs.keySet()) newest = key;
            if (newest == null) return false;
            streamingOutputs.remove(newest);
            return true;
        }
        if (batchWrites.isEmpty()) return false;
        batchWrites.removeLast();
        return true;
    }

    private boolean removeOldestRows() {
        boolean removed = false;
        if (streaming) {
            for (StreamingOutput output : streamingOutputs.values()) {
                if (!output.rows.isEmpty()) {
                    output.rows.subList(0, Math.max(1, output.rows.size() / 4)).clear();
                    output.truncated = true;
                    removed = true;
                }
            }
            return removed;
        }
        for (int index = 0; index < batchWrites.size(); index++) {
            SparkJarTrialPreview.WritePreview write = batchWrites.get(index);
            if (!write.rowsJson().isEmpty()) {
                List<String> rows = new ArrayList<>(write.rowsJson());
                rows.subList(0, Math.max(1, rows.size() / 4)).clear();
                batchWrites.set(index, new SparkJarTrialPreview.WritePreview(
                        write.index(), write.resourceKind(), write.bindingName(), write.target(),
                        write.writeMode(), write.schemaJson(), rows, true));
                removed = true;
            }
        }
        return removed;
    }

    private int serializedBytes() {
        try {
            return objectMapper.writeValueAsBytes(snapshotLocked()).length;
        } catch (Exception exception) {
            throw new RunnerExecutionException(
                    "TRIAL_PREVIEW_SERIALIZATION_FAILED", "试运行输出预览序列化失败", null, exception);
        }
    }

    private SparkJarTrialPreview snapshotLocked() {
        return new SparkJarTrialPreview(writesLocked(), new ArrayList<>(warnings));
    }

    private List<SparkJarTrialPreview.WritePreview> writesLocked() {
        if (!streaming) return new ArrayList<>(batchWrites);
        return streamingOutputs.values().stream().map(StreamingOutput::preview).toList();
    }

    private void addWarning(String warning) {
        if (warnings.size() < 100) warnings.add(warning);
    }

    private static int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private record OutputKey(
            SparkJarTrialPreview.ResourceKind kind,
            String bindingName,
            String target
    ) { }

    private static final class StreamingOutput {
        private final int index;
        private final SparkJarTrialPreview.ResourceKind kind;
        private final String bindingName;
        private final String target;
        private String mode;
        private String schemaJson;
        private final ArrayList<String> rows = new ArrayList<>();
        private boolean truncated;

        private StreamingOutput(int index, SparkJarTrialPreview.ResourceKind kind, String bindingName,
                                String target, String mode, String schemaJson) {
            this.index = index;
            this.kind = kind;
            this.bindingName = bindingName;
            this.target = target;
            this.mode = mode;
            this.schemaJson = schemaJson;
        }

        private SparkJarTrialPreview.WritePreview preview() {
            return new SparkJarTrialPreview.WritePreview(
                    index, kind, bindingName, target, mode, schemaJson,
                    new ArrayList<>(rows), truncated);
        }
    }
}
