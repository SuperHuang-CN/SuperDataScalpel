package cn.superhuang.data.scalpel.contract.page;

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
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {

    public PageResponse {
        content = List.copyOf(content);
    }
}
