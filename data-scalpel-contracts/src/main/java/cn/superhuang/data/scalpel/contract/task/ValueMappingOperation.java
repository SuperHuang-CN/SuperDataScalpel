package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一个逻辑表执行一次静态精确值映射；最多 100 条不同字段规则、每字段最多 200 个映射项、整项最多 2000 个映射项。所有字段规则通过一次最终投影应用，不构成前后依赖。")
public record ValueMappingOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且本处理器不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("1 至 100 条字段映射规则；同一字段只能出现一次。所有规则基于同一来源行独立计算并在一次投影中生效，数组顺序只用于稳定保存和诊断。")
        List<ValueMappingRule> rules
) implements ProcessorOperation {
    public ValueMappingOperation {
        rules = rules == null ? null : List.copyOf(rules);
    }
}
