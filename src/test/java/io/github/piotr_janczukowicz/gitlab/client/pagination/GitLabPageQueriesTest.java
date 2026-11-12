package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.pagination.GitLabKeysetRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabOffsetRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabPageQueriesTest {

    @Test
    void boundPerPage_shouldUseTheConfiguredSize_whenTheRequestSetsNone() {
        final PaginationConfiguration configuration =
                PaginationConfiguration.builder().perPage(40).build();

        final int perPage = GitLabPageQueries.boundPerPage(GitLabPageRequest.byPage(), configuration);

        assertThat(perPage).isEqualTo(40);
    }

    @Test
    void boundPerPage_shouldUseTheExplicitSize_whenTheRequestSetsOne() {
        final PaginationConfiguration configuration =
                PaginationConfiguration.builder().perPage(40).build();

        final int perPage = GitLabPageQueries.boundPerPage(GitLabOffsetRequest.of(2, 7), configuration);

        assertThat(perPage).isEqualTo(7);
    }

    @Test
    void boundPerPage_shouldRefuse_whenTheExplicitSizeIsAboveTheMaximum() {
        final PaginationConfiguration configuration = PaginationConfiguration.defaults();

        final Throwable thrown =
                catchThrowable(() -> GitLabPageQueries.boundPerPage(GitLabOffsetRequest.of(1, 101), configuration));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("100");
    }

    @Test
    void queryValues_shouldKeepTheBaseQueryThenAddPageAndPerPage_whenAnOffsetRequestIsGiven() {
        final Map<String, List<String>> base = new LinkedHashMap<>();
        base.put("state", List.of("opened"));

        final Map<String, List<String>> query = GitLabPageQueries.queryValues(GitLabOffsetRequest.of(4), base, 25);

        assertSoftly(softly -> {
            softly.assertThat(query.keySet()).containsExactly("state", "page", "per_page");
            softly.assertThat(query.get("page")).containsExactly("4");
            softly.assertThat(query.get("per_page")).containsExactly("25");
            softly.assertThat(base).containsOnlyKeys("state");
        });
    }

    @Test
    void queryValues_shouldAddNothing_whenTheRequestSetsNone() {
        final Map<String, List<String>> query =
                GitLabPageQueries.queryValues(GitLabPageRequest.none(), Map.of("owned", List.of("true")), 25);

        assertThat(query).containsOnlyKeys("owned");
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "per_page"})
    void queryValues_shouldRefuse_whenAnOffsetRequestMeetsAPaginationParameterInTheBaseQuery(final String name) {
        final Map<String, List<String>> base = Map.of(name, List.of("9"));

        final Throwable thrown =
                catchThrowable(() -> GitLabPageQueries.queryValues(GitLabOffsetRequest.of(2), base, 30));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pagination", "order_by", "sort", "per_page"})
    void queryValues_shouldRefuse_whenAKeysetRequestMeetsItsParameterInTheBaseQuery(final String name) {
        final Map<String, List<String>> base = Map.of(name, List.of("x"));

        final Throwable thrown =
                catchThrowable(() -> GitLabPageQueries.queryValues(GitLabKeysetRequest.of("id", "asc"), base, 30));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(name);
    }

    @Test
    void queryValues_shouldKeepThePaginationParametersOfTheBaseQuery_whenTheRequestSetsNone() {
        final Map<String, List<String>> base = Map.of("page", List.of("9"), "per_page", List.of("5"));

        final Map<String, List<String>> query = GitLabPageQueries.queryValues(GitLabPageRequest.none(), base, 30);

        assertThat(query).containsOnlyKeys("page", "per_page");
    }

    @Test
    void queryValues_shouldRefuse_whenAnArgumentIsNull() {
        final Throwable thrown = catchThrowable(() -> GitLabPageQueries.queryValues(null, Map.of(), 10));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabPageQueries> constructor = GitLabPageQueries.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
