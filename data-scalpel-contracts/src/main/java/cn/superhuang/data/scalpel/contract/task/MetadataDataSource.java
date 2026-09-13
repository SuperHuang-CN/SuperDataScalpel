package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@JsonClassDescription("供 Canvas 编译和运行准备使用的数据源元数据快照；包含启用状态、能力用途及已解析的表或 TMQ Topic。")
public record MetadataDataSource(
        @JsonPropertyDescription("本次编译元数据快照对应的数据源 UUID。")
        UUID id,
        @JsonPropertyDescription("编译快照生成时该数据源是否处于启用状态。")
        boolean enabled,
        @JsonPropertyDescription("数据源连接结构类型。")
        ConnectionKind connectionKind,
        @JsonPropertyDescription("JDBC 数据库产品类型；非 JDBC 数据源为空。")
        CanvasJdbcDatabaseType jdbcDatabaseType,
        @JsonPropertyDescription("数据源承担的业务用途集合，例如 INPUT、STORAGE 或 OUTPUT；节点只能按所需用途使用。")
        Set<DataSourcePurpose> purposes,
        @JsonPropertyDescription("当前元数据快照包含的 JDBC 表定义；非 JDBC 数据源通常为空列表。")
        List<MetadataTable> tables,
        @JsonPropertyDescription("TDengine TMQ 主题定义快照。")
        List<MetadataTdEngineTmqTopic> tdEngineTmqTopics
) {
    public MetadataDataSource {
        tdEngineTmqTopics = tdEngineTmqTopics == null ? List.of() : List.copyOf(tdEngineTmqTopics);
    }

    public MetadataDataSource(
            UUID id,
            boolean enabled,
            ConnectionKind connectionKind,
            CanvasJdbcDatabaseType jdbcDatabaseType,
            Set<DataSourcePurpose> purposes,
            List<MetadataTable> tables
    ) {
        this(id, enabled, connectionKind, jdbcDatabaseType, purposes, tables, List.of());
    }

    public MetadataDataSource(
            UUID id,
            boolean enabled,
            ConnectionKind connectionKind,
            Set<DataSourcePurpose> purposes,
            List<MetadataTable> tables
    ) {
        this(id, enabled, connectionKind, null, purposes, tables, List.of());
    }
}
