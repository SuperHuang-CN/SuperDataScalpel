package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "平台观测的流式部署状态：STARTING 启动中；RUNNING 运行中；STOPPING 停止中；STOPPED 已停止；FAILED 失败。")
public enum StreamingDeploymentActualState {
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED;

    public boolean active() {
        return this == STARTING || this == RUNNING || this == STOPPING;
    }
}
