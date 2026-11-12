package io.github.piotr_janczukowicz.gitlab.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabKeysetRequestTest {

    @Test
    void putQueryParameters_shouldAddTheKeysetParametersInOrder_whenCreated() {
        final Map<String, String> query = new LinkedHashMap<>();

        GitLabKeysetRequest.of("id", "asc").putQueryParameters(query);

        assertThat(query)
                .containsExactly(
                        Map.entry("pagination", "keyset"), Map.entry("order_by", "id"), Map.entry("sort", "asc"));
    }

    @Test
    void explicitPerPage_shouldBeEmpty_whenNoSizeIsGiven() {
        assertThat(GitLabKeysetRequest.of("id", "desc").explicitPerPage()).isEqualTo(OptionalInt.empty());
    }

    @Test
    void explicitPerPage_shouldBeTheSize_whenOneIsGiven() {
        assertThat(GitLabKeysetRequest.of("id", "desc", 50).explicitPerPage()).isEqualTo(OptionalInt.of(50));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void constructor_shouldRefuse_whenOrderByIsBlank(final String orderBy) {
        final Throwable thrown = catchThrowable(() -> GitLabKeysetRequest.of(orderBy, "asc"));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("orderBy");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "up", "ASC", "descending"})
    void constructor_shouldRefuse_whenSortIsNotAscOrDesc(final String sort) {
        final Throwable thrown = catchThrowable(() -> GitLabKeysetRequest.of("id", sort));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sort");
    }

    @Test
    void constructor_shouldRefuse_whenAnArgumentIsNullOrThePageSizeIsBelowOne() {
        final Throwable noOrder = catchThrowable(() -> GitLabKeysetRequest.of(null, "asc"));
        final Throwable noSort = catchThrowable(() -> GitLabKeysetRequest.of("id", null));
        final Throwable zero = catchThrowable(() -> GitLabKeysetRequest.of("id", "asc", 0));

        assertSoftly(softly -> {
            softly.assertThat(noOrder).isInstanceOf(NullPointerException.class);
            softly.assertThat(noSort).isInstanceOf(NullPointerException.class);
            softly.assertThat(zero).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("perPage");
        });
    }

    @Test
    void kindOf_shouldBeKeyset_whenTheRequestIsKeyset() {
        assertThat(GitLabPageRequestKind.of(GitLabKeysetRequest.of("id", "asc")))
                .isEqualTo(GitLabPageRequestKind.KEYSET);
    }
}
