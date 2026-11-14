package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequestKind;
import io.github.piotr_janczukowicz.json.JsonTypes;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabListSpecTest {

    @Test
    void offset_shouldAllowNoneAndOffsetRequests_whenCreated() {
        final GitLabListSpec<String> spec = GitLabListSpec.offset(JsonTypes.listOf(String.class));

        assertThat(spec.allowedKinds())
                .containsExactlyInAnyOrder(GitLabPageRequestKind.NONE, GitLabPageRequestKind.OFFSET);
    }

    @Test
    void requireAllowed_shouldAcceptARequestOfAnAllowedKind_whenTheSpecAllowsIt() {
        final GitLabListSpec<String> spec = GitLabListSpec.offset(JsonTypes.listOf(String.class));

        final Throwable thrown = catchThrowable(() -> spec.requireAllowed(GitLabPageRequest.byPage()));

        assertThat(thrown).isNull();
    }

    @Test
    void requireAllowed_shouldRefuseARequestOfAnotherKind_whenTheSpecDoesNotAllowIt() {
        final GitLabListSpec<String> spec =
                new GitLabListSpec<>(Set.of(GitLabPageRequestKind.OFFSET), JsonTypes.listOf(String.class));

        final Throwable thrown = catchThrowable(() -> spec.requireAllowed(GitLabPageRequest.none()));

        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NONE")
                .hasMessageContaining("OFFSET");
    }

    @Test
    void constructor_shouldCopyTheAllowedKinds_whenTheSourceSetChangesLater() {
        final Set<GitLabPageRequestKind> source = EnumSet.of(GitLabPageRequestKind.OFFSET);
        final GitLabListSpec<String> spec = new GitLabListSpec<>(source, JsonTypes.listOf(String.class));

        source.add(GitLabPageRequestKind.NONE);

        assertThat(spec.allowedKinds()).containsExactly(GitLabPageRequestKind.OFFSET);
    }

    @Test
    void constructor_shouldRefuse_whenAnArgumentIsNull() {
        final Throwable noKinds = catchThrowable(() -> new GitLabListSpec<>(null, JsonTypes.listOf(String.class)));
        final Throwable noType = catchThrowable(() -> new GitLabListSpec<String>(Set.of(), null));

        assertSoftly(softly -> {
            softly.assertThat(noKinds).isInstanceOf(NullPointerException.class);
            softly.assertThat(noType).isInstanceOf(NullPointerException.class);
        });
    }
}
