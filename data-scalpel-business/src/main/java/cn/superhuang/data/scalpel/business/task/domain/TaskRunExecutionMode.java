package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "运行模式：REAL 正式运行；SIMULATED 模拟运行；TRIAL 受限试运行。")
public enum TaskRunExecutionMode {
    REAL,
    SIMULATED,
    TRIAL
}
