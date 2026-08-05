package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CanvasFilterGroup.class, name = "GROUP"),
        @JsonSubTypes.Type(value = CanvasFieldPredicate.class, name = "PREDICATE")
})
public sealed interface CanvasFilterCondition permits CanvasFilterGroup, CanvasFieldPredicate {
}
