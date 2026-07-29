package cn.superhuang.data.scalpel.contract.task;

public record FileOutputConfiguration(
        String sourceTableName,
        String dataSourceId,
        String targetPath,
        FileOutputConflictPolicy conflictPolicy,
        FileOutputFormatOptions formatOptions
) {
    public FileOutputConfiguration {
        targetPath = FileOutputPaths.normalize(targetPath);
    }
}
