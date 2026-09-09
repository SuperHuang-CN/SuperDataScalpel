package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;

import java.util.List;

public record SpatialStyleFieldProfileResponse(
        SpatialStyleFieldResponse field,
        long totalRowCount,
        long nonNullCount,
        long nullCount,
        List<UniqueValue> uniqueValues,
        boolean truncated,
        String minimum,
        String maximum,
        List<String> breaks,
        int actualClassCount,
        List<String> warnings
) {
    public record UniqueValue(SpatialStyleDocument.ValueType valueType, String value, long count) {
    }
}
