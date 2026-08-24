package cn.superhuang.data.scalpel.contract.task;

/**
 * One Spark JDBC read option configured for a selected physical table.
 *
 * <p>Values deliberately remain strings because Spark JDBC options and JDBC driver properties
 * share the same string-based option boundary.</p>
 */
public record JdbcInputReadOption(String name, String value) {
}
