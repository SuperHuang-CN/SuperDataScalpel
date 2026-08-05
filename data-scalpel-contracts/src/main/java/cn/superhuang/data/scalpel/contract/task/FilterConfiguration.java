package cn.superhuang.data.scalpel.contract.task;

public record FilterConfiguration(
        String sourceTableName,
        String outputTableName,
        CanvasFilterCondition condition
) {
}
