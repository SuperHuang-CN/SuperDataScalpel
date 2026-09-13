package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "单个流式输出查询状态：STARTING 启动中；RUNNING 运行中；STOPPING 停止中；STOPPED 已停止；FAILED 失败。")
public enum StreamingQueryState {
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}
