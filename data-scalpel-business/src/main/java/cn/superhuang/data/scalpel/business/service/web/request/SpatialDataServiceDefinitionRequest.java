package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SpatialDataServiceDefinitionRequest(@NotNull UUID modelId) {
}
