package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "开发套件生成状态：QUEUED 排队；RUNNING 生成中；SUCCEEDED 成功；FAILED 失败；EXPIRED 请求或制品已过期。")
public enum SparkJarDevelopmentKitStatus {
    QUEUED, RUNNING, SUCCEEDED, FAILED, EXPIRED;

    public boolean unfinished() { return this == QUEUED || this == RUNNING; }
}
