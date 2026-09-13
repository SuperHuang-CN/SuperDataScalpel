package cn.superhuang.data.scalpel.business.operations.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "告警严重程度：WARNING 警告，需要关注；CRITICAL 严重，需要优先处理。严重程度影响展示和通知内容，不会自动停止或重跑任务。")
public enum AlertSeverity {
    WARNING, CRITICAL
}
