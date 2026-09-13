package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("批处理等值 Join 配置；连接两张不同的上游逻辑表，使用一个或多个 AND 条件匹配，再按 outputColumns 显式选择、重命名和排列结果字段。相同 Key 在任一侧出现多行时会产生所有匹配组合，不做隐式去重。")

public record JoinConfiguration(
        @JsonPropertyDescription("Join 左侧上游逻辑表名；必须存在，并且不能与 rightTableName 相同。")
        String leftTableName,
        @JsonPropertyDescription("Join 右侧上游逻辑表名；必须存在，并且不能与 leftTableName 相同。")
        String rightTableName,
        @JsonPropertyDescription("Join 结果的 Canvas 逻辑表名；必须与进入节点时已有的所有表名不同，成功后作为新表加入表集合。")
        String outputTableName,
        @JsonPropertyDescription("必填 Join 类型：INNER 只保留匹配记录，LEFT/RIGHT 保留对应一侧全部记录，FULL 保留两侧全部记录；外连接未匹配侧的输出字段为 NULL。")
        JoinType joinType,
        @JsonPropertyDescription("至少一个左右字段等值条件，数组中的条件全部使用 AND 组合。使用 Spark SQL 普通等号语义，因此任一侧为 NULL 都不匹配，包括 NULL 与 NULL。重复的同一字段对无效。")
        List<JoinCondition> conditions,
        @JsonPropertyDescription("结果字段投影；至少一项 included=true。启用项按数组顺序输出，可选择左右同名字段并分别改名；最终输出名按大小写不敏感规则唯一，同一侧同一来源字段最多配置一次。")
        List<JoinOutputColumn> outputColumns
) {
    public JoinConfiguration {
        conditions = conditions == null ? null : List.copyOf(conditions);
        outputColumns = outputColumns == null ? null : List.copyOf(outputColumns);
    }

    public JoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            JoinType joinType,
            List<JoinCondition> conditions
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions, List.of());
    }
}
