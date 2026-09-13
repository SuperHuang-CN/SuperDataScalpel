package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Spark-free severity used by the Admin-to-Task-Engine online Java compiler contract. */
@JsonClassDescription("在线源码诊断级别：ERROR 阻断 JAR 生成，WARNING 为非阻断风险，NOTE 为编译器提示。")
public enum SparkJarSourceDiagnosticSeverity {
    ERROR,
    WARNING,
    NOTE
}
