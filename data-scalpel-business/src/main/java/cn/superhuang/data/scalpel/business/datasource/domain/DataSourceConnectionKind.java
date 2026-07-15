package cn.superhuang.data.scalpel.business.datasource.domain;

/** Technical connection family used to select its configuration and runtime capabilities. */
public enum DataSourceConnectionKind {
    JDBC,
    KAFKA,
    S3
}
