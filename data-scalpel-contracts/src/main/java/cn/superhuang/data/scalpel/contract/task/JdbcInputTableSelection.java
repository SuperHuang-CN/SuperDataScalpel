package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * One physical table selected by a JDBC input node.
 *
 * <p>The object boundary keeps each physical table's read tuning independent. Partitioned reads
 * intentionally remain a future, dedicated configuration instead of raw JDBC options.</p>
 */
@JsonClassDescription("JDBC 输入选择的一张物理表或视图及其专属读取参数；完整读取结果以原始 tableName 作为 Canvas 表名，Schema 来自运行准备时的元数据快照。")
public record JdbcInputTableSelection(
        @JsonPropertyDescription("当前数据源固定数据库和 Schema 中的原始物理表或视图名称；不能提交 catalog.schema.table、已引用标识符或自定义 SQL，同一输入节点不能重复。")
        String tableName,
        @JsonPropertyDescription("只作用于本表的 Spark JDBC 读取参数，最多 32 项；允许受控参数和非敏感未知驱动参数，禁止连接、凭据、目标表、写入及分片边界参数。空数组表示完全使用数据源和平台默认配置。")
        List<JdbcInputReadOption> readOptions
) {
    public JdbcInputTableSelection {
        readOptions = readOptions == null ? List.of() : List.copyOf(readOptions);
    }

    public JdbcInputTableSelection(String tableName) {
        this(tableName, List.of());
    }
}
