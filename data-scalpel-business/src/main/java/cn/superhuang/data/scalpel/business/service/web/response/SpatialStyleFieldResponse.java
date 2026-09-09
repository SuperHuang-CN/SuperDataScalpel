package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;

public record SpatialStyleFieldResponse(
        String code,
        String name,
        String dataType,
        SpatialStyleDocument.ValueType valueType,
        boolean uniqueValueSupported,
        boolean classBreaksSupported,
        boolean labelSupported
) {
}
