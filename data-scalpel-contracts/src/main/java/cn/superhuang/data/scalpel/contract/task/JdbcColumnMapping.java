package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("JDBC、模型输出及快照同步中的一项显式字段映射；运行时按目标 Schema 对来源值执行 Spark Cast 并以目标字段名投影。相同来源可映射到多个目标，但每个目标在同一映射列表中只能出现一次。")
public record JdbcColumnMapping(
        @JsonPropertyDescription("来源字段名；必须存在于当前操作引用的上游逻辑表 Schema 中。未选择的来源字段不会写入目标。")
        String sourceColumnName,
        @JsonPropertyDescription("目标字段名；必须存在、非自增、非生成，并在当前映射列表中唯一。来源值会显式 Cast 为目标平台类型；Geometry 只能映射到类型、CRS 和维度完全一致的 Geometry，实际标量值转换仍可能在运行时失败。")
        String targetColumnName
) {
}
