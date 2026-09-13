package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("对一张 BOUNDED 逻辑表执行一次去重。空 keyColumns 表示按全行去重且只允许 ANY；非空 Key 可使用 ANY 任意保留，或用 FIRST/LAST 和必填排序选一行。排序键并列且缺少唯一终结字段时，具体保留行不保证跨重跑稳定。")
public record DeduplicateOperation(
        @JsonPropertyDescription("当前操作的稳定 UUID，在本节点内唯一，用于关联配置、校验问题和字段血缘；不能使用空值或任意业务名称替代。")
        String operationId,
        @JsonPropertyDescription("进入本节点前已经存在的 BOUNDED 上游 Canvas 逻辑表名；同一节点内每张来源表最多配置一次。")
        String sourceTableName,
        @JsonPropertyDescription("结果表处理方式。REPLACE_SOURCE 替换来源 Map 项且不能改表名；CREATE_NEW_TABLE 保留来源并要求提供不冲突的新表名。")
        ProcessorOutput output,
        @JsonPropertyDescription("识别重复组的来源字段名数组，各字段必须存在、不得重复且不能是 Geometry。空数组表示比较完整行，此时只允许 ANY，且来源表不能包含 Geometry。")
        List<String> keyColumns,
        @JsonPropertyDescription("必填保留策略：ANY 任意保留且 orderBy 必须为空；FIRST 按 orderBy 取第一行；LAST 把每项方向和 NULL 顺序同时反转后取第一行。")
        DeduplicateKeepStrategy keepStrategy,
        @JsonPropertyDescription("FIRST/LAST 必填且至少一项的来源字段排序规则，字段不得重复或为 Geometry；ANY 时必须为空。数组顺序形成多字段比较优先级，但并列时不提供隐式稳定终结顺序。")
        List<SortField> orderBy
) implements ProcessorOperation {
    public DeduplicateOperation {
        keyColumns = keyColumns == null ? null : List.copyOf(keyColumns);
        orderBy = orderBy == null ? null : List.copyOf(orderBy);
    }
}
