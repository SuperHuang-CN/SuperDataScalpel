package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Map;

/** Common public page response used by standard and SQL query services. */
@JsonClassDescription("标准表或 SQL 数据服务一次查询的分页结果；包含实际页码、分页大小、可选总数和当前页记录。")
public record ServiceQueryResponse(
        @JsonPropertyDescription("页码，从 1 开始。")
        int pageNo,
        @JsonPropertyDescription("每页最多返回的记录数。")
        int pageSize,
        @JsonPropertyDescription("符合查询条件的总记录数；请求未要求统计或 SQL 试运行预览时为空。")
        Long totalCount,
        @JsonPropertyDescription("当前页结果行；每行仅包含服务定义允许返回的字段。")
        List<Map<String, Object>> items
) {

    public ServiceQueryResponse {
        items = List.copyOf(items);
    }
}
