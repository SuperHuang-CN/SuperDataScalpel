package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("JDBC 表输入配置；从一个已启用且具有 SOURCE 用途的数据源全量读取一张或多张物理表或视图，每个选择产生一张以原始 tableName 命名的 BOUNDED 逻辑表。流任务中也只在启动时读取为静态有界表。")

public record JdbcInputConfiguration(
        @JsonPropertyDescription("读取使用的 JDBC 数据源 UUID 字符串；草稿可为空，编译前必须解析为已启用、连接类型为 JDBC 且具有 SOURCE 用途的数据源。")
        String dataSourceId,
        @JsonPropertyDescription("要读取的物理表或视图；至少一项，tableName 在本数组中精确匹配后不能重复，且都必须属于 dataSourceId 固定的数据库和 Schema。每项独立应用 readOptions。")
        List<JdbcInputTableSelection> tables
) {
    public JdbcInputConfiguration {
        tables = tables == null ? null : List.copyOf(tables);
    }
}
