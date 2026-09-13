package cn.superhuang.data.scalpel.business.datasource.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "资源浏览器类型：JDBC 表、TDengine 超级表、Kafka Topic、HTTP API 资源、空间资源或无浏览能力")
public enum DataSourceResourceBrowserKind {
    JDBC_TABLES,
    TDENGINE_SUPERTABLES,
    KAFKA_TOPICS,
    API_RESOURCES,
    SPATIAL_RESOURCES,
    NONE
}
