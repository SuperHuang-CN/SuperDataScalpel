package cn.superhuang.data.scalpel.contract.search;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/**
 * Shared request model for entity list searches.
 *
 * <p>{@code search} uses DataScalpel's simple DSL. {@code page} is zero based;
 * {@code sort} is a comma-separated list where a leading {@code -} means descending.</p>
 */
public record SearchRequest(
        @JsonPropertyDescription("可选实体 Search DSL；支持比较、字符串匹配以及 AND/OR/NOT，省略或空白表示不增加客户端条件。")
        String search,
        @JsonPropertyDescription("从 0 开始的页码；省略时为 0。")
        Integer page,
        @JsonPropertyDescription("每页数量；省略时为 20，最大 500。")
        Integer size,
        @JsonPropertyDescription("逗号分隔的标量字段排序；字段名前加 - 表示倒序，省略时按 id 倒序。")
        String sort
) {

    public static SearchRequest empty() {
        return new SearchRequest(null, null, null, null);
    }
}
