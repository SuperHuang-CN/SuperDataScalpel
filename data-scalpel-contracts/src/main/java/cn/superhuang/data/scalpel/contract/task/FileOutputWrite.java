package cn.superhuang.data.scalpel.contract.task;

/** One independently addressable file sink in a file output node. */
public record FileOutputWrite(
        String writeId,
        String sourceTableName,
        String targetPath,
        FileOutputConflictPolicy conflictPolicy,
        FileOutputFormatOptions formatOptions
) {
    public FileOutputWrite {
        targetPath = FileOutputPaths.normalize(targetPath);
    }
}
