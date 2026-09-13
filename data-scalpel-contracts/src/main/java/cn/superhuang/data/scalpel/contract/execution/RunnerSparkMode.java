package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Task Engine Spark 运行模式：LOCAL 进程内本地 Spark；CLUSTER 由外部集群后端承载。")
public enum RunnerSparkMode {
    LOCAL,
    CLUSTER
}
