package cn.superhuang.data.scalpel.business.standard.web.request;

import jakarta.validation.constraints.Min;

import java.util.UUID;

public record MoveStandardDictionaryItemRequest(
        @Min(1) int expectedVersion,
        UUID targetParentId,
        @Min(0) int targetIndex
) {
}
