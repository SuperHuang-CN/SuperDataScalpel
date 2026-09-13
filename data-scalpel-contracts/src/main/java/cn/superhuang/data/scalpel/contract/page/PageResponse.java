package cn.superhuang.data.scalpel.contract.page;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Stable, framework-independent response for a paged list.
 *
 * @param content returned items
 * @param totalElements total matching item count
 * @param totalPages total page count
 * @param page zero-based current page number
 * @param size requested page size
 * @param <T> item type
 */
public record PageResponse<T>(
        @JsonPropertyDescription("当前页数据项，顺序与请求排序一致；没有匹配数据时为空列表。")
        List<T> content,
        @JsonPropertyDescription("满足查询条件的数据总数，不受当前分页位置影响。")
        long totalElements,
        @JsonPropertyDescription("按当前 size 计算的总页数；没有匹配数据时为 0。")
        int totalPages,
        @JsonPropertyDescription("当前零基页码；第一页为 0。")
        int page,
        @JsonPropertyDescription("当前请求的每页条数上限。")
        int size
) {

    public PageResponse {
        content = List.copyOf(content);
    }
}
