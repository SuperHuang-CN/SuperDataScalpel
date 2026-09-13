package cn.superhuang.data.scalpel.business.filedataset.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Durable queue lifecycle for one file dataset table parsing attempt group. */
@Schema(description = "文件解析作业状态：QUEUED 首次等待或重试退避；RUNNING 已被 Worker 领取且租约尚待回收；SUCCEEDED 成功；FAILED 遇到不可重试错误或耗尽次数；CANCELLED 在开始前被业务操作取消。")
public enum FileDatasetParseJobStatus {

    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
