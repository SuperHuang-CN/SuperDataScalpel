package cn.superhuang.data.scalpel.business.task.web.request;

import jakarta.validation.constraints.Size;

import java.util.List;

public record QueryTaskFieldLineageRequest(
        String flowKey,
        @Size(max = 50, message = "一次最多查询 50 个字段") List<String> outputFieldKeys
) {
}
