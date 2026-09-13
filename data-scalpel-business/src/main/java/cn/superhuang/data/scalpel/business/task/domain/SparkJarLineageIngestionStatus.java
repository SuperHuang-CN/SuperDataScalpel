package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Spark JAR 运行血缘摄取状态：QUEUED 排队；RUNNING 分析中；SUCCEEDED 完成；FAILED 失败；STALE 结果已因定义变化失效。")
public enum SparkJarLineageIngestionStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    STALE
}
