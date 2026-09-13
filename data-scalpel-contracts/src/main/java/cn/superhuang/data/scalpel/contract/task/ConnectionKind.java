package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("数据源连接种类：JDBC 数据库、HTTP_API、KAFKA 或 S3 兼容对象存储；它决定连接配置分支和可发现资源类型。")
public enum ConnectionKind {
    JDBC,
    HTTP_API,
    KAFKA,
    S3
}
