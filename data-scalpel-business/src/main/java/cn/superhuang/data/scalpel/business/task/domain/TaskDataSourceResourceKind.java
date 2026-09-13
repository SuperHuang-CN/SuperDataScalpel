package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "Canvas 节点引用的数据源资源种类：JDBC_TABLE 物理表；JDBC_QUERY 自定义查询；HTTP_API_RESOURCE 已登记 HTTP API；SPATIAL_RESOURCE 空间服务图层；KAFKA_TOPIC Kafka 主题；TDENGINE_TMQ_TOPIC TDengine TMQ 主题；FILE_PATH 文件数据源内路径。")
public enum TaskDataSourceResourceKind {
    JDBC_TABLE,
    JDBC_QUERY,
    HTTP_API_RESOURCE,
    SPATIAL_RESOURCE,
    KAFKA_TOPIC,
    TDENGINE_TMQ_TOPIC,
    FILE_PATH
}
