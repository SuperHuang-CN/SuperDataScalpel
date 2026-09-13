package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("批处理文件输出配置；向同一个已启用且具有 DISTRIBUTION 用途的 S3 数据源声明一项或多项独立目录写入。输出不产生下游表；多项写入按顺序执行且没有跨目标事务，前项可能已成功而后项失败。")
public record FileOutputConfiguration(
        @JsonPropertyDescription("接收全部文件的对象存储数据源 UUID 字符串；草稿可为空，编译前必须解析为已启用、连接类型为 S3 且具有 DISTRIBUTION 用途的数据源。")
        String dataSourceId,
        @JsonPropertyDescription("按数组顺序执行的独立目标目录写入，至少一项；每项 writeId 在节点内唯一。任一写入失败时已提交目录不回滚，后续项跳过。")
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
