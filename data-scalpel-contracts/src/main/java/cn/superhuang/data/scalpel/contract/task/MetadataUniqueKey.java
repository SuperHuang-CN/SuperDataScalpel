package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("元数据快照中的一个候选唯一键；记录其来自主键还是唯一索引，以及有序物理列组合。")
public record MetadataUniqueKey(
        @JsonPropertyDescription("数据库主键或唯一索引名称；驱动未提供名称时可能为空。")
        String name,
        @JsonPropertyDescription("唯一键来源：PRIMARY_KEY 为数据库主键，UNIQUE_INDEX 为唯一索引。")
        MetadataUniqueKeyType type,
        @JsonPropertyDescription("按数据库键顺序排列的物理列名；组合唯一键包含多个值。")
        List<String> columns
) {
    public MetadataUniqueKey {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
