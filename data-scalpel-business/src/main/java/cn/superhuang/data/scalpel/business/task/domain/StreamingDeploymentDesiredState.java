package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "流式部署目标状态：RUNNING 期望持续运行；STOPPED 期望停止。")
public enum StreamingDeploymentDesiredState {
    RUNNING,
    STOPPED
}
