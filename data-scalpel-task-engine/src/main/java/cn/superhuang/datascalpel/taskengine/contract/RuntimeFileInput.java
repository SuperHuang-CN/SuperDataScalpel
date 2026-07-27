package cn.superhuang.datascalpel.taskengine.contract;



import java.util.List;
import java.util.UUID;

/** One stable logical table plus its ordered immutable physical sources. */
public record RuntimeFileInput(
        UUID fileDatasetId,
        UUID fileDatasetTableId,
        String schemaFingerprint,
        RuntimeFileParsingOptions parsingOptions,
        List<RuntimeFileSource> sources
) implements java.io.Serializable {

    public RuntimeFileInput {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public RuntimeFileInput forSource(RuntimeFileSource source) {
        return new RuntimeFileInput(
                fileDatasetId, fileDatasetTableId, schemaFingerprint, parsingOptions, List.of(source)
        );
    }

    public RuntimeFileSource source() {
        if (sources.size() != 1) {
            throw new IllegalStateException("当前读取上下文必须且只能包含一个文件来源");
        }
        return sources.getFirst();
    }

    public UUID sourceFileId() { return source().sourceFileId(); }
    public FileDatasetFormat format() { return source().format(); }
    public FileDatasetCompression compression() { return source().compression(); }
    public FileDatasetStorageKind storageKind() { return source().storageKind(); }
    public String objectKey() { return source().objectKey(); }
    public String materializedPrefix() { return source().materializedPrefix(); }
    public String sourceKey() { return source().sourceKey(); }
}
