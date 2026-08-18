package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

import java.util.UUID;

public record QualitySampleColumn(
        UUID fieldId,
        String code,
        String name,
        PlatformTypeDefinition type,
        boolean primaryKey,
        boolean diagnostic
) {
    public QualitySampleColumn {
        if (code == null || code.isBlank() || code.length() > 200 || name == null || name.isBlank()
                || name.length() > 200 || type == null || diagnostic != (fieldId == null)
                || diagnostic && primaryKey) {
            throw new IllegalArgumentException("质检样本字段元数据无效");
        }
    }
}
