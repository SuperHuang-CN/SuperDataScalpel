package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("仅用于 BATCH、BOUNDED 小数据完整快照的模型同步输出配置。它与 JDBC 快照同步使用相同的映射、Cast、Key、比较、删除保护和单事务执行语义；目标限定为 PostgreSQL 或 MySQL 上已发布的 MANAGED 模型。")
public record ModelSnapshotSyncOutputConfiguration(
        @JsonPropertyDescription("完整来源快照的上游 Canvas 逻辑表名；必须引用直接上游合并结果中的 BOUNDED 表，流式或局部范围数据不能用于本节点。")
        String sourceTableName,
        @JsonPropertyDescription("目标模型 UUID 字符串；模型必须处于 PUBLISHED 状态且物理模式为 MANAGED，其已启用 JDBC 数据源必须具有 STORAGE 用途并使用 PostgreSQL 或 MySQL。")
        String targetModelId,
        @JsonPropertyDescription("用于匹配来源和模型记录的 1 到 32 个有序目标模型字段 code，不得为空或重复；每项必须已映射，且不能是 Geometry、自增或生成字段。字段元数据允许 NULL 或组合不匹配模型主键时仅警告，但运行数据中的来源和目标 Key 都必须非 NULL 且各自唯一。Key 变化表现为旧行 DELETE 和新行 INSERT。")
        List<String> keyColumns,
        @JsonPropertyDescription("来源字段到可写模型字段的显式映射；来源值先 Cast 到模型目标类型，再用于 Key 校验、精确比较和写入。未映射来源字段忽略，未映射模型字段不比较或更新，插入时依赖目标默认值或 nullable 约束。")
        List<JdbcColumnMapping> columnMappings,
        @JsonPropertyDescription("模型物理表中存在而完整来源快照中不存在的记录处理策略；必须提供。KEEP 保留目标独有行，DELETE 仅在非空来源保护和两项阈值均通过后删除。")
        SnapshotDeletePolicy deletePolicy
) implements SnapshotSyncConfiguration {
    public ModelSnapshotSyncOutputConfiguration {
        keyColumns = keyColumns == null ? List.of() : List.copyOf(keyColumns);
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
    }
}
