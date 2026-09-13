package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Spark JAR 受控资源绑定类型：MODEL 平台模型；JDBC_DATA_SOURCE JDBC 数据源；KAFKA_TOPIC Kafka 数据源中的主题。")
public enum SparkJarResourceType {
    MODEL,
    JDBC_DATA_SOURCE,
    KAFKA_TOPIC
}
