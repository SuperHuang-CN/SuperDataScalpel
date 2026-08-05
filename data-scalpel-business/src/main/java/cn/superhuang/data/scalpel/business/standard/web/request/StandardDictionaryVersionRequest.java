package cn.superhuang.data.scalpel.business.standard.web.request;

import jakarta.validation.constraints.Min;

public record StandardDictionaryVersionRequest(@Min(1) int expectedVersion) {
}
