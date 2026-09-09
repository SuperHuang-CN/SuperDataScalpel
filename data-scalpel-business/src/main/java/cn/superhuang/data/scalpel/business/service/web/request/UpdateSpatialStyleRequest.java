package cn.superhuang.data.scalpel.business.service.web.request;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.service.domain.SpatialStyleMode;
import jakarta.validation.Valid;

public record UpdateSpatialStyleRequest(SpatialStyleMode mode, @Valid SpatialStyleDocument styleDocument) {
}
