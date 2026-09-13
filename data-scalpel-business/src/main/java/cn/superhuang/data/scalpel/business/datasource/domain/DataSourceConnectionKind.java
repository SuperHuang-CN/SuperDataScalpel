package cn.superhuang.data.scalpel.business.datasource.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Technical connection family used to select its configuration and runtime capabilities. */
@Schema(description = "连接配置结构：JDBC 数据库、KAFKA 集群、S3 对象存储、HTTP_API HTTP 或空间服务")
public enum DataSourceConnectionKind {
    JDBC,
    KAFKA,
    S3,
    HTTP_API
}
