package cn.superhuang.data.scalpel.business.lineage.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "外部血缘资源类型：Kafka Topic、文件数据集表、HTTP API 资源、空间服务资源、对象存储路径或 JDBC 查询结果。")
public enum LineageExternalResourceType {
    KAFKA_TOPIC,
    FILE_DATASET_TABLE,
    HTTP_API_RESOURCE,
    SPATIAL_SERVICE_RESOURCE,
    OBJECT_STORAGE_PATH,
    JDBC_QUERY_RESULT
}
