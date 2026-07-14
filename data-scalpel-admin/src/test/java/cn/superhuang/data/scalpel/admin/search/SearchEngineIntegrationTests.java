package cn.superhuang.data.scalpel.admin.search;

import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.InvalidSearchRequestException;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class SearchEngineIntegrationTests {

    @Autowired
    private SearchEngine searchEngine;

    @Autowired
    private SearchFixtureRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.saveAll(List.of(
                new SearchFixture("a", "task-alpha", 2, SearchFixtureState.READY,
                        LocalDate.of(2026, 7, 1), null),
                new SearchFixture("b", "task-beta", 9, SearchFixtureState.READY,
                        LocalDate.of(2026, 7, 2), "reviewed"),
                new SearchFixture("c", "task-gamma", 5, SearchFixtureState.WAITING,
                        LocalDate.of(2026, 7, 3), null),
                new SearchFixture("d", "report-delta", 1, SearchFixtureState.FINISHED,
                        LocalDate.of(2026, 7, 4), null)
        ));
    }

    @Test
    void searchesTypedValuesAndEscapedTextWithPagingAndSort() {
        Page<SearchFixture> page = searchEngine.search(
                new SearchRequest("state:\"READY\" AND name:*\"task\"*", 0, 20, "-priority"),
                SearchFixture.class,
                repository
        );

        assertThat(page.getContent()).extracting(SearchFixture::getId).containsExactly("b", "a");
        assertThat(page.getContent()).extracting(SearchFixture::getPriority).containsExactly(9, 2);
    }

    @Test
    void supportsParenthesesComparisonsNullChecksAndFixedBusinessConditions() {
        Page<SearchFixture> page = searchEngine.search(
                new SearchRequest("(state:\"READY\" OR state:\"WAITING\") AND createdDate>=\"2026-07-02\" AND note:null", 0, 20, "id"),
                SearchFixture.class,
                repository,
                (root, query, builder) -> builder.greaterThan(root.get("priority"), 1)
        );

        assertThat(page.getContent()).extracting(SearchFixture::getId).containsExactly("c");
    }

    @Test
    void supportsTheRemainingComparisonAndTextOperators() {
        Page<SearchFixture> comparisonPage = searchEngine.search(
                new SearchRequest("priority>\"1\" AND priority<\"9\" AND priority>=\"2\" AND priority<=\"5\" AND name!\"task-beta\"", 0, 20, "id"),
                SearchFixture.class,
                repository
        );
        Page<SearchFixture> startsWithPage = searchEngine.search(
                new SearchRequest("name:\"task-\"*", 0, 20, "id"),
                SearchFixture.class,
                repository
        );
        Page<SearchFixture> endsWithPage = searchEngine.search(
                new SearchRequest("name:*\"gamma\"", 0, 20, "id"),
                SearchFixture.class,
                repository
        );
        Page<SearchFixture> notNullPage = searchEngine.search(
                new SearchRequest("note!null", 0, 20, "id"),
                SearchFixture.class,
                repository
        );

        assertThat(comparisonPage.getContent()).extracting(SearchFixture::getId).containsExactly("a", "c");
        assertThat(startsWithPage.getContent()).extracting(SearchFixture::getId).containsExactly("a", "b", "c");
        assertThat(endsWithPage.getContent()).extracting(SearchFixture::getId).containsExactly("c");
        assertThat(notNullPage.getContent()).extracting(SearchFixture::getId).containsExactly("b");
    }

    @Test
    void defaultsToDescendingIdAndAddsIdAsTheStableSortTieBreaker() {
        Page<SearchFixture> defaultSort = searchEngine.search(
                SearchRequest.empty(), SearchFixture.class, repository);
        Page<SearchFixture> explicitSort = searchEngine.search(
                new SearchRequest(null, 0, 20, "priority"), SearchFixture.class, repository);

        assertThat(defaultSort.getContent()).extracting(SearchFixture::getId).containsExactly("d", "c", "b", "a");
        assertThat(explicitSort.getPageable().getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void rejectsUnknownFieldsUnsupportedOperatorsAndInvalidPageParameters() {
        assertThatThrownBy(() -> searchEngine.search(
                new SearchRequest("missing:\"value\"", 0, 20, null),
                SearchFixture.class,
                repository
        )).isInstanceOf(InvalidSearchRequestException.class);

        assertThatThrownBy(() -> searchEngine.search(
                new SearchRequest("priority:*\"9\"*", 0, 20, null),
                SearchFixture.class,
                repository
        )).isInstanceOf(InvalidSearchRequestException.class);

        assertThatThrownBy(() -> searchEngine.search(
                new SearchRequest(null, -1, 20, null),
                SearchFixture.class,
                repository
        )).isInstanceOf(InvalidSearchRequestException.class);

        assertThatThrownBy(() -> searchEngine.search(
                new SearchRequest(null, 0, SearchEngine.MAX_PAGE_SIZE + 1, null),
                SearchFixture.class,
                repository
        )).isInstanceOf(InvalidSearchRequestException.class);
    }
}
