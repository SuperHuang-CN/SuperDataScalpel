package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一张逻辑表执行一次派生投影；有效规则为全局规则加本项规则，全部表达式独立读取原始来源行。目标字段已存在时在原位置覆盖，不存在时按规则顺序追加；派生字段类型和 nullable 由 Spark Analyzer 决定。")
public record DeriveColumnsOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("本来源表专属的有序派生规则；可为空但与 globalDerivations 合并后至少一项且最多 100 项。目标名在有效规则中唯一，表达式不能引用同一操作或全局规则刚生成的字段。")
        List<ColumnDerivation> derivations
) implements ProcessorOperation {
    public DeriveColumnsOperation {
        derivations = derivations == null ? null : List.copyOf(derivations);
    }
}
