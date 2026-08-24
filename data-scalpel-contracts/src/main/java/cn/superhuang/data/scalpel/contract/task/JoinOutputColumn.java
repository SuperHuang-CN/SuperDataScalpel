package cn.superhuang.data.scalpel.contract.task;

public record JoinOutputColumn(
        JoinOutputColumnSource sourceSide,
        String sourceColumnName,
        String outputColumnName,
        boolean included
) {
}
