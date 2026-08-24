package cn.superhuang.data.scalpel.contract.task;

public record FileOutputConfiguration(
        String dataSourceId,
        java.util.List<FileOutputWrite> writes
) {
    public FileOutputConfiguration {
        writes = writes == null ? java.util.List.of() : java.util.List.copyOf(writes);
    }

    public FileOutputConfiguration(
            String sourceTableName,
            String dataSourceId,
            String targetPath,
            FileOutputConflictPolicy conflictPolicy,
            FileOutputFormatOptions formatOptions
    ) {
        this(dataSourceId, java.util.List.of(new FileOutputWrite(
                java.util.UUID.randomUUID().toString(), sourceTableName, targetPath,
                conflictPolicy, formatOptions)));
    }

    public String sourceTableName() { return writes.isEmpty() ? null : writes.getFirst().sourceTableName(); }
    public String targetPath() { return writes.isEmpty() ? null : writes.getFirst().targetPath(); }
    public FileOutputConflictPolicy conflictPolicy() { return writes.isEmpty() ? null : writes.getFirst().conflictPolicy(); }
    public FileOutputFormatOptions formatOptions() { return writes.isEmpty() ? null : writes.getFirst().formatOptions(); }
}
