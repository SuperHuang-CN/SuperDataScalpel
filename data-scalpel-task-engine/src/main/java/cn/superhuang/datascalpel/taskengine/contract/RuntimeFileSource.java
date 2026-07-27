package cn.superhuang.datascalpel.taskengine.contract;



import java.io.Serializable;
import java.util.UUID;

public record RuntimeFileSource(
        UUID tableSourceId,
        UUID sourceFileId,
        FileDatasetFormat format,
        FileDatasetCompression compression,
        FileDatasetStorageKind storageKind,
        String objectKey,
        String materializedPrefix,
        String sourceKey
) implements Serializable {
}
