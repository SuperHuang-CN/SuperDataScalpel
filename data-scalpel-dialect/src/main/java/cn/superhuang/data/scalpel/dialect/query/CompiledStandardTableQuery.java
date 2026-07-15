package cn.superhuang.data.scalpel.dialect.query;

/** A compiled standard query together with the requested public pagination values. */
public record CompiledStandardTableQuery(int pageNo, int pageSize, StandardQuery query) {
}
