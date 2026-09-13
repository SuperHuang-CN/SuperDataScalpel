package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** A trusted runtime value resolved by the Canvas Task Engine. */
@JsonClassDescription("引用由 Task Engine 注入且同一 Attempt 所有表和行共享的受控运行时常量；它不是客户端变量，也不是流任务的微批时间。编译预览使用零 UUID 和 Unix Epoch 占位值。")
public record RuntimeValueExpression(
        @JsonPropertyDescription("必填白名单值：EXECUTION_ID 产生当前执行 UUID 的 STRING；EXECUTION_STARTED_AT 产生本次执行入口捕获的 UTC TIMESTAMP。")
        CanvasRuntimeValue value
) implements CanvasExpression {
}
