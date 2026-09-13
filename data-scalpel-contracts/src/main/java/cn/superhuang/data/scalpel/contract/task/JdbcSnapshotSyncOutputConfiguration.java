package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("仅用于 BATCH、BOUNDED 小数据完整快照的 JDBC 同步输出配置。来源字段先映射并 Cast 到目标类型，再按 Key 比较：来源独有行 INSERT，Key 相同且比较字段变化时 UPDATE，相同时不写，目标独有行按 deletePolicy 保留或删除。Runner 在严格表锁和单个目标事务中按 DELETE、UPDATE、INSERT 执行，任一步失败整体回滚；部署规模限制不保存在本配置中。")
public record JdbcSnapshotSyncOutputConfiguration(
        @JsonPropertyDescription("完整来源快照的上游 Canvas 逻辑表名；必须引用直接上游合并结果中的 BOUNDED 表，流式或局部范围数据不能用于本节点。")
        String sourceTableName,
        @JsonPropertyDescription("目标 JDBC 数据源 UUID 字符串；必须指向已启用、具有 DISTRIBUTION 用途的 PostgreSQL 或 MySQL 数据源。")
        String dataSourceId,
        @JsonPropertyDescription("接收同步结果的普通物理表名；视图等只读对象不支持。同步不会自动建表、演进 Schema 或创建唯一约束。")
        String targetTableName,
        @JsonPropertyDescription("用于匹配来源和目标记录的 1 到 32 个有序目标字段名，不得为空或重复；每项必须已映射，且不能是 Geometry、自增或生成字段。字段元数据允许 NULL 或组合不匹配数据库唯一约束时仅警告，但运行数据中的来源和目标 Key 都必须非 NULL 且各自唯一。Key 变化表现为旧行 DELETE 和新行 INSERT。")
        List<String> keyColumns,
        @JsonPropertyDescription("来源字段到可写目标字段的显式映射；来源值先 Cast 到目标平台类型，再用于 Key 校验、精确比较和写入。未映射来源字段忽略，未映射目标字段不比较或更新，插入时依赖目标默认值或 nullable 约束。")
        List<JdbcColumnMapping> columnMappings,
        @JsonPropertyDescription("目标中存在而完整来源快照中不存在的记录处理策略；必须提供。KEEP 保留目标独有行，DELETE 仅在非空来源保护和两项阈值均通过后删除。")
        SnapshotDeletePolicy deletePolicy
) implements SnapshotSyncConfiguration {
    public JdbcSnapshotSyncOutputConfiguration {
        keyColumns = keyColumns == null ? List.of() : List.copyOf(keyColumns);
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }
}
