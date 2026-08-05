package cn.superhuang.data.scalpel.contract.task;

public record ShapefileAttributeMapping(
        String sourceColumnName,
        String targetFieldName,
        Integer targetStringByteLength
) {
}
