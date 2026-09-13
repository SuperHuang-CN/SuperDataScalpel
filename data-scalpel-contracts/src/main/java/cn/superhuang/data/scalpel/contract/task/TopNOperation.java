package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一张 BOUNDED 逻辑表执行一次全局或分组 Top N。EXACT 最多保留 N 行但完全并列时具体行不稳定；WITH_TIES 使用 rank 并保留 rank<=N，边界并列可使每组结果超过 N 行。")
public record TopNOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的 BOUNDED 上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("可为空的来源分组字段数组；空数组表示整表全局 Top N，非空表示按字段组合形成的每组分别计算。字段必须存在、不得重复且不能是 Geometry。")
        List<String> partitionByColumns,
        @JsonPropertyDescription("必填且至少一项的来源字段排序规则；字段必须存在、不得重复或为 Geometry，数组顺序、ASC/DESC 与 NULLS FIRST/LAST 共同决定名次。完全并列时没有隐式稳定终结顺序。")
        List<SortField> orderBy,
        @JsonPropertyDescription("全局或每个分组保留的名次上限，闭区间 1 到 1000000。WITH_TIES 可能因第 N 名并列而输出超过该行数。")
        int limit,
        @JsonPropertyDescription("必填的边界并列策略：EXACT 使用 limit 或 row_number，每组最多 limit 行；WITH_TIES 使用 rank 并保留 rank<=limit，包含边界处全部同排序键记录。")
        TopNTieStrategy tieStrategy
) implements ProcessorOperation {
    public TopNOperation {
        partitionByColumns = partitionByColumns == null ? null : List.copyOf(partitionByColumns);
        orderBy = orderBy == null ? null : List.copyOf(orderBy);
    }
}
