package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一个逻辑表执行一次空值处理；1 至 100 条规则严格按数组顺序作用于前一条规则的结果，因此先填充再删行与先删行再填充可能产生不同记录。输出保留字段顺序、来源、有界性、事件时间和 Watermark。")
public record NullHandlingOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且本处理器不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("严格按顺序执行的 1 至 100 条 NULL 规则；DROP_ROW 可重复检查字段，FILL_LITERAL 对同一字段最多配置一次。")
        List<NullHandlingRule> rules
) implements ProcessorOperation {
    public NullHandlingOperation {
        rules = rules == null ? null : List.copyOf(rules);
    }
}
