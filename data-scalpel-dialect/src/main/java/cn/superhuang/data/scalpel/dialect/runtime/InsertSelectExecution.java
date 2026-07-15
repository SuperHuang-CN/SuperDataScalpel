package cn.superhuang.data.scalpel.dialect.runtime;

/** Result reported by a controlled insert-select JDBC execution. */
public record InsertSelectExecution(long affectedRows) {
}
