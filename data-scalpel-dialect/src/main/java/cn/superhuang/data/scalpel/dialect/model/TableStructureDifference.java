package cn.superhuang.data.scalpel.dialect.model;

/** One explicit difference between a model definition and an inspected physical table. */
public record TableStructureDifference(
        String column,
        TableStructureDifferenceType type,
        String expected,
        String actual
) {
}
