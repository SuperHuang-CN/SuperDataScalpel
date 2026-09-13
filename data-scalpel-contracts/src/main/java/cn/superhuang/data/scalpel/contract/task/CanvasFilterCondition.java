package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CanvasFilterGroup.class, name = "GROUP"),
        @JsonSubTypes.Type(value = CanvasFieldPredicate.class, name = "PREDICATE")
})
@JsonClassDescription("FILTER 的结构化条件联合，以 kind 判别：GROUP 用 AND/OR 递归组合至少一个子条件，PREDICATE 对来源字段执行类型化比较。整棵树最多 12 层、256 个节点；不支持字段间比较、运行参数或自定义函数。")
public sealed interface CanvasFilterCondition permits CanvasFilterGroup, CanvasFieldPredicate {
}
