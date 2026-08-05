package cn.superhuang.data.scalpel.contract.task;

public record AggregateItem(
        AggregateFunction function,
        String sourceColumnName,
        String outputColumnName,
        boolean distinct
) {
}
