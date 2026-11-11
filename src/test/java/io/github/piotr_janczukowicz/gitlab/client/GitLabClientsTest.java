package io.github.piotr_janczukowicz.gitlab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProvider;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabClientsTest {

    private static final AuthenticationProvider AUTH = AuthenticationProviders.bearer("token");
    private static final String AGENT = "gitlab-test/1.0";

    @Test
    void create_shouldDeriveGitlabComAndNoTier_whenOnlyAuthAndUserAgentAreGiven() {
        try (GitLabClient client = GitLabClients.create(AUTH, AGENT)) {
            assertSoftly(softly -> {
                softly.assertThat(client.flavor()).isEqualTo(GitLabFlavor.GITLAB_COM);
                softly.assertThat(client.tier()).isEmpty();
                softly.assertThat(client.pagination()).isEqualTo(PaginationConfiguration.defaults());
            });
        }
    }

    @Test
    void create_shouldDeriveSelfManaged_whenBaseUriIsAnotherHost() {
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(URI.create("https://gitlab.example.test"))
                .build();

        try (GitLabClient client = GitLabClients.create(AUTH, AGENT, configuration)) {
            assertThat(client.flavor()).isEqualTo(GitLabFlavor.SELF_MANAGED);
        }
    }

    @Test
    void create_shouldPreferTheExplicitFlavorAndKeepTheTier_whenBothAreConfigured() {
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(URI.create("https://gitlab.example.test"))
                .flavor(GitLabFlavor.DEDICATED)
                .tier(GitLabTier.PREMIUM)
                .build();

        try (GitLabClient client = GitLabClients.create(AUTH, AGENT, configuration)) {
            assertSoftly(softly -> {
                softly.assertThat(client.flavor()).isEqualTo(GitLabFlavor.DEDICATED);
                softly.assertThat(client.tier()).contains(GitLabTier.PREMIUM);
            });
        }
    }

    @Test
    void create_shouldRefuse_whenBaseUriIsPlainHttpToARemoteHost() {
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(URI.create("http://gitlab.example.test"))
                .build();

        final Throwable thrown = catchThrowable(() -> GitLabClients.create(AUTH, AGENT, configuration));

        assertThat(thrown)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("plain http")
                .hasMessageContaining("allowInsecureHttp");
    }

    @Test
    void create_shouldAccept_whenPlainHttpIsAllowed() {
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(URI.create("http://gitlab.example.test"))
                .allowInsecureHttp(true)
                .build();

        try (GitLabClient client = GitLabClients.create(AUTH, AGENT, configuration)) {
            assertThat(client.flavor()).isEqualTo(GitLabFlavor.SELF_MANAGED);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8080", "http://127.0.0.1:8080", "http://app.localhost"})
    void create_shouldAccept_whenPlainHttpTargetsTheLocalMachine(final String baseUri) {
        final GitLabClientConfiguration configuration =
                GitLabClientConfiguration.builder().baseUri(URI.create(baseUri)).build();

        try (GitLabClient client = GitLabClients.create(AUTH, AGENT, configuration)) {
            assertThat(client.flavor()).isEqualTo(GitLabFlavor.SELF_MANAGED);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "agent\r\nX-Evil: 1", "café/1.0"})
    void create_shouldRefuse_whenUserAgentIsNotVisibleAscii(final String userAgent) {
        final Throwable thrown = catchThrowable(() -> GitLabClients.create(AUTH, userAgent));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_shouldRefuse_whenAuthIsNull() {
        final Throwable thrown = catchThrowable(() -> GitLabClients.create(null, AGENT));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_shouldRefuse_whenConfigurationIsNull() {
        final Throwable thrown = catchThrowable(() -> GitLabClients.create(AUTH, AGENT, null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void create_shouldRefuse_whenDefaultHeadersSetTheUserAgent() {
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .defaultHeaders(HttpHeaders.of("User-Agent", "other/1.0"))
                .build();

        final Throwable thrown = catchThrowable(() -> GitLabClients.create(AUTH, AGENT, configuration));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("User-Agent");
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabClients> constructor = GitLabClients.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
