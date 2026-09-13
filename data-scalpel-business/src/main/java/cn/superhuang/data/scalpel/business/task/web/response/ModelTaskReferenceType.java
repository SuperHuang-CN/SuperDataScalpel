package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "任务引用模型的来源：LOCAL_SQL_INPUT/OUTPUT 为本地 SQL 输入或输出；CANVAS_NODE 为 Canvas 节点；MODEL_QUALITY_TARGET 为质检目标；SPARK_JAR_RESOURCE_BINDING 为 JAR 模型绑定；CURRENT_LINEAGE 为当前未退役血缘快照补充发现的引用。")

public enum ModelTaskReferenceType {
    LOCAL_SQL_INPUT,
    LOCAL_SQL_OUTPUT,
    CANVAS_NODE,
    MODEL_QUALITY_TARGET,
    SPARK_JAR_RESOURCE_BINDING,
    CURRENT_LINEAGE
}
