package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record QueryDataServiceFieldLineageRequest(
        @Size(max = 50, message = "一次最多查询 50 个字段") List<UUID> fieldIds,
        Integer depth
) {
}
