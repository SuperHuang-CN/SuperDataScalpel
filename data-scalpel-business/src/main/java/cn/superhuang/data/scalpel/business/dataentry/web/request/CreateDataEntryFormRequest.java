package cn.superhuang.data.scalpel.business.dataentry.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateDataEntryFormRequest(@NotNull UUID modelId) {
}
