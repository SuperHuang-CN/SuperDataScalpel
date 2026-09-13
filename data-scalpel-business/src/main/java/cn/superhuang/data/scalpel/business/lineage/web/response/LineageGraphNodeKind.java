package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "血缘节点类型：模型、JDBC 表、外部资源、任务、字段或数据服务")

public enum LineageGraphNodeKind {
    MODEL,
    JDBC_TABLE,
    EXTERNAL_RESOURCE,
    TASK,
    FIELD,
    DATA_SERVICE
}
