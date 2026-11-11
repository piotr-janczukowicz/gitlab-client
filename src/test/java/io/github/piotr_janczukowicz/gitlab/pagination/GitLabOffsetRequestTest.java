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
class GitLabOffsetRequestTest {

    @Test
    void byPage_shouldAskForTheFirstPageWithTheConfiguredSize_whenCalled() {
        final GitLabPageRequest request = GitLabPageRequest.byPage();

        assertThat(request).isEqualTo(GitLabOffsetRequest.of(1));
    }

    @Test
    void none_shouldAddNoParameters_whenCalled() {
        final GitLabPageRequest request = GitLabPageRequest.none();
        final Map<String, String> query = new LinkedHashMap<>();

        request.putQueryParameters(query);

        assertSoftly(softly -> {
            softly.assertThat(request).isInstanceOf(GitLabNoneRequest.class);
            softly.assertThat(query).isEmpty();
            softly.assertThat(request.explicitPerPage()).isEmpty();
        });
    }

    @Test
    void putQueryParameters_shouldAddThePageNumber_whenAnOffsetRequestIsGiven() {
        final Map<String, String> query = new LinkedHashMap<>();

        GitLabOffsetRequest.of(3).putQueryParameters(query);

        assertThat(query).containsExactly(Map.entry("page", "3"));
    }

    @Test
    void explicitPerPage_shouldBeTheSizeOfTheRequest_whenOneWasGiven() {
        final OptionalInt explicit = GitLabOffsetRequest.of(2, 25).explicitPerPage();

        assertThat(explicit).hasValue(25);
    }

    @Test
    void explicitPerPage_shouldBeEmpty_whenNoSizeWasGiven() {
        final OptionalInt explicit = GitLabOffsetRequest.of(2).explicitPerPage();

        assertThat(explicit).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_shouldRefuse_whenThePageIsBelowOne(final int page) {
        final Throwable thrown = catchThrowable(() -> GitLabOffsetRequest.of(page));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("page");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_shouldRefuse_whenThePageSizeIsBelowOne(final int perPage) {
        final Throwable thrown = catchThrowable(() -> GitLabOffsetRequest.of(1, perPage));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("perPage");
    }

    @Test
    void kindOf_shouldNameTheKindOfEveryRequest_whenRequestsAreGiven() {
        assertSoftly(softly -> {
            softly.assertThat(GitLabPageRequestKind.of(GitLabPageRequest.none()))
                    .isEqualTo(GitLabPageRequestKind.NONE);
            softly.assertThat(GitLabPageRequestKind.of(GitLabPageRequest.byPage()))
                    .isEqualTo(GitLabPageRequestKind.OFFSET);
        });
    }
}
