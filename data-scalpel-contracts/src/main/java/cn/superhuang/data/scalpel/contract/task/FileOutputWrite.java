package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** One independently addressable file sink in a file output node. */
@JsonClassDescription("文件输出节点中的一次独立目录写入；把一张 BOUNDED 来源表按严格格式写到 S3 根前缀下的目标目录。一个目标是完整目录数据集或单套空间制品，不应把 part 文件当作独立结果。")
public record FileOutputWrite(
        @JsonPropertyDescription("本次写入的稳定 UUID 字符串，在所属节点内唯一；用于运行指标、诊断和血缘身份，编辑其他字段时不应重新生成。")
        String writeId,
        @JsonPropertyDescription("要写出的上游 Canvas 逻辑表名；必须引用进入输出节点前已经存在的 BOUNDED 表。")
        String sourceTableName,
        @JsonPropertyDescription("相对于 S3 数据源根前缀的目标目录，输入会 trim、合并连续斜杠并去除末尾斜杠。规范化后最长 1024 字符，不能以 / 开头，不能含反斜杠、协议、查询串、片段或空段、.、..、_temporary。")
        String targetPath,
        @JsonPropertyDescription("以整个 targetPath 前缀为单位处理冲突：FAIL_IF_EXISTS 在目录已有任何业务文件或完成标记时拒绝；OVERWRITE 删除或替换已有目标。S3 OVERWRITE 不是原子目录替换，失败时可能留下空目录或部分结果。")
        FileOutputConflictPolicy conflictPolicy,
        @JsonPropertyDescription("必填的严格判别格式配置；type 决定只允许 CSV、JSON_LINES、PARQUET、SHAPEFILE、GEOPARQUET 或 GEOJSON 对应分支字段。")
        FileOutputFormatOptions formatOptions
) {
    public FileOutputWrite {
        targetPath = FileOutputPaths.normalize(targetPath);
    }
}
