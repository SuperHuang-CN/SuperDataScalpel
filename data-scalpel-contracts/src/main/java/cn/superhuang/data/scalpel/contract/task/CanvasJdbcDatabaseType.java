package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Canvas JDBC 元数据快照中的数据库实现；TDENGINE_WEBSOCKET 和 TDENGINE_RESTFUL 表示两种 TDengine JDBC 连接方式，其余值对应各关系数据库方言。")
public enum CanvasJdbcDatabaseType {
    POSTGRESQL,
    HIGHGO,
    MYSQL,
    ORACLE,
    SQL_SERVER,
    CLICKHOUSE,
    DAMENG,
    OPENGAUSS,
    KINGBASE,
    TDENGINE_WEBSOCKET,
    TDENGINE_RESTFUL
}
