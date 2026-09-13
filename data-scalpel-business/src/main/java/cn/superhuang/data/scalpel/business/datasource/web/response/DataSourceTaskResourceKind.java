package cn.superhuang.data.scalpel.business.datasource.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "任务引用资源种类：MODEL 经模型存储间接引用；JDBC_DATA_SOURCE 绑定整个 JDBC 数据源；JDBC_TABLE 读取表；JDBC_QUERY 执行查询；HTTP_API_RESOURCE 调用已登记 API；SPATIAL_RESOURCE 读取空间服务资源；KAFKA_TOPIC 或 TDENGINE_TMQ_TOPIC 消费/写入 Topic；FILE_PATH 写入文件路径。")
public enum DataSourceTaskResourceKind {
    MODEL,
    JDBC_DATA_SOURCE,
    JDBC_TABLE,
    JDBC_QUERY,
    HTTP_API_RESOURCE,
    SPATIAL_RESOURCE,
    KAFKA_TOPIC,
    TDENGINE_TMQ_TOPIC,
    FILE_PATH
}
