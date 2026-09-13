package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "TDengine TMQ 消费组清理状态：PENDING 等待；RUNNING 清理中；SUCCESS 成功；FAILED 失败。")
public enum TmqConsumerGroupCleanupState {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}
