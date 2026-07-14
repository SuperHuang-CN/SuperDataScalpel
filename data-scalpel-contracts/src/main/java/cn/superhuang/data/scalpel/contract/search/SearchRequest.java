package cn.superhuang.data.scalpel.contract.search;

/**
 * Shared request model for entity list searches.
 *
 * <p>{@code search} uses DataScalpel's simple DSL. {@code page} is zero based;
 * {@code sort} is a comma-separated list where a leading {@code -} means descending.</p>
 */
public record SearchRequest(
        String search,
        Integer page,
        Integer size,
        String sort
) {

    public static SearchRequest empty() {
        return new SearchRequest(null, null, null, null);
    }
}
