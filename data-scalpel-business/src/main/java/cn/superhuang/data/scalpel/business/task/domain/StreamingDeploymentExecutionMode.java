package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "流式部署模式：REAL 正式部署；TRIAL 受限试运行。")
public enum StreamingDeploymentExecutionMode {
    REAL,
    TRIAL
}
