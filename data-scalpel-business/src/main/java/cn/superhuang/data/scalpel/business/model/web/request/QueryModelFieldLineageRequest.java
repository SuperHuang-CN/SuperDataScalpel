package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.business.lineage.web.request.LineageDirection;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record QueryModelFieldLineageRequest(
        @Size(max = 50, message = "一次最多查询 50 个字段") List<UUID> fieldIds,
        LineageDirection direction,
        Integer depth
) {
}
