package io.github.piotr_janczukowicz.gitlab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabApiBaseTest {

    @ParameterizedTest
    @CsvSource({
        "https://gitlab.com,https://gitlab.com/api/v4/",
        "https://gitlab.com/,https://gitlab.com/api/v4/",
        "https://gitlab.example.test:8443,https://gitlab.example.test:8443/api/v4/",
        "https://host.example.test/gitlab,https://host.example.test/gitlab/api/v4/",
        "https://host.example.test/gitlab/,https://host.example.test/gitlab/api/v4/",
        "https://host.example.test/a/b,https://host.example.test/a/b/api/v4/",
        "https://gitlab.com/api/v4,https://gitlab.com/api/v4/",
        "https://gitlab.com/api/v4/,https://gitlab.com/api/v4/",
        "https://host.example.test/gitlab/api/v4,https://host.example.test/gitlab/api/v4/",
        "http://localhost:8080,http://localhost:8080/api/v4/"
    })
    void apiUri_shouldEndInTheApiRoot_whenInstanceRootIsGiven(final String instanceRoot, final String expected) {
        final GitLabApiBase base = GitLabApiBase.of(URI.create(instanceRoot));

        final URI apiUri = base.apiUri();

        assertThat(apiUri).hasToString(expected);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://gitlab.com?private_token=secret",
                "https://gitlab.com/gitlab?x=1",
                "https://gitlab.com#fragment",
                "https://user:secret@gitlab.com",
                "gitlab.com",
                "/gitlab",
                "ftp://gitlab.com",
                "mailto:someone@example.test",
                "https:///gitlab",
                "https:gitlab"
            })
    void of_shouldRefuse_whenInstanceRootIsNotAPlainHttpOrigin(final String instanceRoot) {
        final URI uri = URI.create(instanceRoot);

        final Throwable thrown = catchThrowable(() -> GitLabApiBase.of(uri));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
    }

    @Test
    void of_shouldRefuse_whenInstanceRootIsNull() {
        final Throwable thrown = catchThrowable(() -> GitLabApiBase.of(null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }
}
