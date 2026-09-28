package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

/**
 * 通过 JDBC 连接绑定读取表、执行查询或写入结果。
 * @apiGroup 读写 JDBC
 * @apiOrder 20
 * @apiExample var rows = context.jdbc().readTable("source_db", "orders");
 *   context.jdbc().write("target_db", rows)
 *       .table(JdbcTableIdentifier.table("orders_copy"))
 *       .mode(JdbcWriteMode.APPEND).execute();
 * @apiNote 绑定名指定连接，表名由代码指定；连接凭据由平台提供。
 */
public interface JdbcResources {
    /**
     * 读取指定表；未传读取选项时使用默认配置。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param table 目标物理表名称或结构化表标识。
     * @param options 本次读取选项，可使用 JdbcReadOptions.defaults()。
     * @apiExample var rows = context.jdbc().readTable("source_db",
     *       JdbcTableIdentifier.table("orders"), JdbcReadOptions.defaults());
     *   rows.show(20, false);
     */
    Dataset<Row> readTable(String bindingName, JdbcTableIdentifier table, JdbcReadOptions options);

    /**
     * 读取指定表；未传读取选项时使用默认配置。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param table 目标物理表名称或结构化表标识。
     * @apiExample var rows = context.jdbc().readTable("source_db", JdbcTableIdentifier.table("orders"));
     *   rows.show(20, false);
     */
    default Dataset<Row> readTable(String bindingName, JdbcTableIdentifier table) {
        return readTable(bindingName, table, JdbcReadOptions.defaults());
    }

    /**
     * 读取指定表；未传读取选项时使用默认配置。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param table 目标物理表名称或结构化表标识。
     * @param options 本次读取选项，可使用 JdbcReadOptions.defaults()。
     * @apiExample var rows = context.jdbc().readTable("source_db", "orders", JdbcReadOptions.defaults());
     *   rows.show(20, false);
     */
    default Dataset<Row> readTable(String bindingName, String table, JdbcReadOptions options) {
        return readTable(bindingName, JdbcTableIdentifier.table(table), options);
    }

    /**
     * 读取指定表；未传读取选项时使用默认配置。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param table 目标物理表名称或结构化表标识。
     * @apiExample var rows = context.jdbc().readTable("source_db", "orders");
     *   rows.show(20, false);
     */
    default Dataset<Row> readTable(String bindingName, String table) {
        return readTable(bindingName, JdbcTableIdentifier.table(table));
    }

    /**
     * 读取指定表；未传读取选项时使用默认配置。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param schema 表所属 Schema。
     * @param table 目标物理表名称或结构化表标识。
     * @param options 本次读取选项，可使用 JdbcReadOptions.defaults()。
     * @apiExample var rows = context.jdbc().readTable("source_db", "public", "orders", JdbcReadOptions.defaults());
     *   rows.show(20, false);
     */
    default Dataset<Row> readTable(String bindingName, String schema, String table, JdbcReadOptions options) {
        return readTable(bindingName, JdbcTableIdentifier.schemaTable(schema, table), options);
    }

    /**
     * 读取指定表；未传读取选项时使用默认配置。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param schema 表所属 Schema。
     * @param table 目标物理表名称或结构化表标识。
     * @apiExample var rows = context.jdbc().readTable("source_db", "public", "orders");
     *   rows.show(20, false);
     */
    default Dataset<Row> readTable(String bindingName, String schema, String table) {
        return readTable(bindingName, JdbcTableIdentifier.schemaTable(schema, table));
    }

    /**
     * 将 SQL 查询结果读取为 Dataset；未传选项时使用默认配置。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param sql 要读取的 SQL 查询，由调用者保证正确性。
     * @param options 本次读取选项，可使用 JdbcReadOptions.defaults()。
     * @apiExample var rows = context.jdbc().readQuery("source_db", "SELECT id FROM orders",
     *       JdbcReadOptions.builder().fetchSize(1000).build());
     *   rows.show(20, false);
     */
    Dataset<Row> readQuery(String bindingName, String sql, JdbcReadOptions options);

    /**
     * 将 SQL 查询结果读取为 Dataset；未传选项时使用默认配置。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param sql 要读取的 SQL 查询，由调用者保证正确性。
     * @apiExample var rows = context.jdbc().readQuery("source_db", "SELECT id FROM orders");
     *   rows.show(20, false);
     */
    default Dataset<Row> readQuery(String bindingName, String sql) {
        return readQuery(bindingName, sql, JdbcReadOptions.defaults());
    }

    /**
     * 创建 JDBC 写入配置，设置目标表后调用 execute()。
     * @param bindingName 任务资源的代码引用名，区分大小写；不是资源显示名称。
     * @param source 待写入的 Dataset，字段需要满足目标映射。
     * @apiExample var rows = context.jdbc().readTable("source_db", "orders");
     *   context.jdbc().write("target_db", rows)
     *       .table(JdbcTableIdentifier.table("orders_copy"))
     *       .mode(JdbcWriteMode.APPEND).execute();
     * @apiNote execute() 会执行写入；请先确认目标表和写入方式。
     */
    JdbcWriteOperation write(String bindingName, Dataset<Row> source);
}
