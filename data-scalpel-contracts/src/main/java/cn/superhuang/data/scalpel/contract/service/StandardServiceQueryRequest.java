package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;

import java.util.List;

/** Fixed public V1 request body for every standard table service. */
public record StandardServiceQueryRequest(
        @JsonPropertyDescription("页码，从 1 开始。")
        Integer pageNo,
        @JsonPropertyDescription("每页最多返回的记录数。")
        Integer pageSize,
        @JsonPropertyDescription("希望响应返回的服务字段编码列表；空列表表示使用服务定义中的全部可查询字段。")
        List<String> fields,
        @JsonPropertyDescription("结构化过滤表达式；为空时不增加过滤条件。")
        @Valid StandardFilterNode filter,
        @JsonPropertyDescription("结构化排序列表；为空时使用服务定义的稳定默认顺序。")
        List<@Valid StandardOrder> sort,
        @JsonPropertyDescription("分组字段编码列表；不聚合时为空。")
        List<String> groupBy,
        @JsonPropertyDescription("聚合计算列表；不聚合时为空。")
        List<@Valid StandardAggregator> aggregates,
        @JsonPropertyDescription("是否额外计算并返回满足当前筛选条件的总记录数。")
        Boolean returnCount
) {

    public StandardServiceQueryRequest {
        fields = fields == null ? List.of() : List.copyOf(fields);
        sort = sort == null ? List.of() : List.copyOf(sort);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        aggregates = aggregates == null ? List.of() : List.copyOf(aggregates);
    }
}
