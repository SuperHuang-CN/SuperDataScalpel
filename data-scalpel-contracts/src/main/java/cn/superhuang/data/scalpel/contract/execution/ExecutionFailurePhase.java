package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Stable execution phase used by result artifacts, events and task-run diagnostics. */
@JsonClassDescription("执行失败阶段：PREPARE 准备运行资源；READ 读取输入；PROCESS 计算处理；WRITE 写入目标；DELIVERY 投递结果或事件；DISPATCH 控制面向执行后端提交。")
public enum ExecutionFailurePhase {
    PREPARE,
    READ,
    PROCESS,
    WRITE,
    DELIVERY,
    DISPATCH
}
