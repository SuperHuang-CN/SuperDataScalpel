package cn.superhuang.data.scalpel.business.service.web.request;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Read-only compilation of the current draft; does not save or apply a style. */
public record QuerySpatialStyleSldRequest(@NotNull @Valid SpatialStyleDocument styleDocument) {
}
