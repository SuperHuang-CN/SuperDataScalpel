package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Canvas 编译问题级别：ERROR 阻止任务定义成为可执行计划，WARNING 允许编译继续但说明存在降级或风险。")
public enum CompilationSeverity {
    ERROR,
    WARNING
}
