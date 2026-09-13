package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("流式 Stream-Static Join 配置；使用 AND 组合的普通等值条件，把左侧 UNBOUNDED 流与右侧 BOUNDED 静态表连接，再显式投影结果字段。静态表只在流任务启动时读取，不会自动刷新；当前不支持两个无界流连接。")

public record StreamJoinConfiguration(
        @JsonPropertyDescription("左侧 UNBOUNDED 流逻辑表名。其事件时间字段若从 LEFT 侧启用输出则按输出名传播 Watermark；若被排除则结果清除事件时间和 Watermark。")
        String leftTableName,
        @JsonPropertyDescription("右侧 BOUNDED 静态逻辑表名；任务启动时读取一次且运行期间不自动刷新，不能引用另一个无界流。")
        String rightTableName,
        @JsonPropertyDescription("连接结果的 Canvas 逻辑表名；必须与进入节点时已有的所有表名不同，结果始终为 UNBOUNDED。")
        String outputTableName,
        @JsonPropertyDescription("必填 Stream Join 类型：INNER 只输出匹配的流记录，LEFT 还保留未匹配的左侧流记录并把右侧输出字段设为 NULL；不支持 RIGHT 或 FULL。")
        StreamJoinType joinType,
        @JsonPropertyDescription("至少一个左右字段等值条件，全部使用 AND 组合。使用 Spark SQL 普通等号，因此任一侧为 NULL 都不匹配；不支持范围、OR、时间区间或 Stream-Stream 状态条件。")
        List<JoinCondition> conditions,
        @JsonPropertyDescription("结果字段投影；至少一项 included=true，启用项按数组顺序输出。最终名称按大小写不敏感规则唯一，同一侧同一来源字段最多配置一次；右侧静态表的事件时间信息不会传播。")
        List<JoinOutputColumn> outputColumns
) {
    public StreamJoinConfiguration {
        conditions = conditions == null ? null : List.copyOf(conditions);
        outputColumns = outputColumns == null ? null : List.copyOf(outputColumns);
    }

    public StreamJoinConfiguration(
            String leftTableName,
            String rightTableName,
            String outputTableName,
            StreamJoinType joinType,
            List<JoinCondition> conditions
    ) {
        this(leftTableName, rightTableName, outputTableName, joinType, conditions, List.of());
    }
}
