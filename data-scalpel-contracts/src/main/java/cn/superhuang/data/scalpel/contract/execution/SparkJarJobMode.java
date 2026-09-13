package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("用户 Spark JAR 作业模式：BATCH 有界批处理；STREAMING 持续运行的 Structured Streaming 作业。必须与任务类型和 JAR 元数据一致。")
public enum SparkJarJobMode {
    BATCH,
    STREAMING
}
