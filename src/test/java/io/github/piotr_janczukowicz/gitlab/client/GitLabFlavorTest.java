package io.github.piotr_janczukowicz.gitlab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabFlavorTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"https://gitlab.com", "https://gitlab.com/", "https://GitLab.COM", "https://gitlab.com/api/v4"})
    void of_shouldBeGitlabCom_whenHostIsGitlabCom(final String instanceRoot) {
        final URI uri = URI.create(instanceRoot);

        final GitLabFlavor flavor = GitLabFlavor.of(uri);

        assertThat(flavor).isEqualTo(GitLabFlavor.GITLAB_COM);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://gitlab.example.test",
                "https://gitlab.com.evil.example",
                "https://evilgitlab.com",
                "https://www.gitlab.com",
                "https://tenant.gitlab-dedicated.example.test",
                "http://localhost:8080",
                "https://host.example.test/gitlab.com"
            })
    void of_shouldBeSelfManaged_whenHostIsNotGitlabCom(final String instanceRoot) {
        final URI uri = URI.create(instanceRoot);

        final GitLabFlavor flavor = GitLabFlavor.of(uri);

        assertThat(flavor).isEqualTo(GitLabFlavor.SELF_MANAGED);
    }

    @Test
    void of_shouldNeverInferDedicated_whenOnlyTheHostIsKnown() {
        final URI uri = URI.create("https://acme.gitlab-dedicated.com");

        final GitLabFlavor flavor = GitLabFlavor.of(uri);

        assertThat(flavor).isNotEqualTo(GitLabFlavor.DEDICATED);
    }

    @Test
    void of_shouldRefuse_whenUriIsNull() {
        final Throwable thrown = catchThrowable(() -> GitLabFlavor.of(null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }
}
