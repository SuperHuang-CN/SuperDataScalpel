package cn.superhuang.data.scalpel.contract.task;

import java.util.UUID;

/** Stable model-field identity carried beside the logical Canvas schema. */
public record MetadataModelField(
        UUID id,
        String code,
        String name,
        int sortOrder
) {
}
