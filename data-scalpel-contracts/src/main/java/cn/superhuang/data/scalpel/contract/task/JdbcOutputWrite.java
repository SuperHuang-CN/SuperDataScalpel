package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/** One independently addressable JDBC sink in a JDBC output node. */
@JsonClassDescription("JDBC 输出节点中的一次独立物理表写入；先按目标 Schema 对映射字段执行显式 Cast，再按 APPEND、OVERWRITE 或 UPSERT 写入。未映射的来源字段被忽略，不自动创建目标表或演进 Schema。")
public record JdbcOutputWrite(
        @JsonPropertyDescription("本次写入的稳定 UUID 字符串，在所属节点内唯一；用于运行指标、诊断和流式查询/Checkpoint 身份，编辑其他字段时不应重新生成。")
        String writeId,
        @JsonPropertyDescription("要写出的上游 Canvas 逻辑表名；必须引用进入输出节点前已经存在的表。")
        String sourceTableName,
        @JsonPropertyDescription("dataSourceId 固定数据库和 Schema 中已经存在的物理表名；视图及其他不可写对象无效，节点不会自动建表。")
        String targetTableName,
        @JsonPropertyDescription("必填写入模式。APPEND 追加；OVERWRITE 先 TRUNCATE 再追加且两个步骤不保证原子性；UPSERT 按完整唯一键插入或更新。实时任务只允许 APPEND/UPSERT。")
        JdbcWriteMode writeMode,
        @JsonPropertyDescription("来源字段到目标字段的显式映射，至少一项。目标字段不能重复、自增或生成；目标中不可空且无默认值的可写字段必须全部覆盖。来源值先 Cast 为目标类型，未映射来源字段忽略。")
        List<JdbcColumnMapping> columnMappings,
        @JsonPropertyDescription("UPSERT 时用于匹配目标记录的目标字段数组，必须按顺序完整等于目标表当前一组主键或安全唯一索引，并全部出现在 columnMappings 中；不能包含自增、生成或 Geometry 字段。非 UPSERT 模式必须为空数组。")
        List<String> upsertKeyColumns
) {
    public JdbcOutputWrite {
        columnMappings = columnMappings == null ? List.of() : List.copyOf(columnMappings);
        upsertKeyColumns = upsertKeyColumns == null ? List.of() : List.copyOf(upsertKeyColumns);
    }
}
