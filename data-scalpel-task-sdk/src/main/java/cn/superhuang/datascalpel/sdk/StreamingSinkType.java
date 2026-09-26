package cn.superhuang.datascalpel.sdk;

/**
 * 声明流式查询的目标种类。
 * @apiGroup 实时处理
 * @apiMode STREAMING
 */
public enum StreamingSinkType {
    /**
     * 写入 Kafka。
     */
    KAFKA,
    /**
     * 写入 JDBC 数据库。
     */
    JDBC,
    /**
     * 自定义目标；副作用不在 SDK 试运行拦截范围内。
     */
    CUSTOM
}
