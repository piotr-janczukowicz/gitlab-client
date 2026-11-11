package io.github.piotr_janczukowicz.gitlab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitListener;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.client.HttpCallObserver;
import io.github.piotr_janczukowicz.http.transport.HttpTransportConfiguration;
import io.github.piotr_janczukowicz.http.transport.RateLimitDetector;
import io.github.piotr_janczukowicz.http.transport.RetryConfiguration;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabClientConfigurationTest {

    @Test
    void defaults_shouldTargetGitlabCom_whenNothingIsSet() {
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.defaults();

        assertSoftly(softly -> {
            softly.assertThat(configuration.baseUri()).isEqualTo(URI.create("https://gitlab.com"));
            softly.assertThat(configuration.defaultHeaders()).isEqualTo(HttpHeaders.empty());
            softly.assertThat(configuration.transport())
                    .isEqualTo(HttpTransportConfiguration.builder().build());
            softly.assertThat(configuration.retry()).isEqualTo(RetryConfiguration.defaults());
            softly.assertThat(configuration.mutationInterval()).isEqualTo(Duration.ZERO);
            softly.assertThat(configuration.conditionalCacheMaxEntries()).isEqualTo(64);
            softly.assertThat(configuration.conditionalCacheMaxBodyBytes()).isEqualTo(1024 * 1024);
            softly.assertThat(configuration.conditionalCacheMaxBytes()).isEqualTo(4L * 1024 * 1024);
            softly.assertThat(configuration.callObserver()).isNull();
            softly.assertThat(configuration.allowInsecureHttp()).isFalse();
            softly.assertThat(configuration.flavor()).isEmpty();
            softly.assertThat(configuration.tier()).isEmpty();
            softly.assertThat(configuration.pagination()).isEqualTo(PaginationConfiguration.defaults());
        });
    }

    @Test
    void build_shouldKeepEveryValue_whenTheyAreSet() {
        final HttpCallObserver observer = event -> {};
        final HttpHeaders headers = HttpHeaders.of("X-Team", "platform");
        final PaginationConfiguration pagination =
                PaginationConfiguration.builder().perPage(50).build();
        final HttpTransportConfiguration transport =
                HttpTransportConfiguration.builder().maxConcurrentRequests(4).build();

        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(URI.create("https://gitlab.example.test/gitlab"))
                .defaultHeaders(headers)
                .transport(transport)
                .mutationInterval(Duration.ofMillis(250))
                .conditionalCacheMaxEntries(8)
                .conditionalCacheMaxBodyBytes(2048)
                .conditionalCacheMaxBytes(65_536L)
                .callObserver(observer)
                .allowInsecureHttp(true)
                .flavor(GitLabFlavor.DEDICATED)
                .tier(GitLabTier.ULTIMATE)
                .pagination(pagination)
                .build();

        assertSoftly(softly -> {
            softly.assertThat(configuration.baseUri()).isEqualTo(URI.create("https://gitlab.example.test/gitlab"));
            softly.assertThat(configuration.defaultHeaders()).isEqualTo(headers);
            softly.assertThat(configuration.transport()).isEqualTo(transport);
            softly.assertThat(configuration.mutationInterval()).isEqualTo(Duration.ofMillis(250));
            softly.assertThat(configuration.conditionalCacheMaxEntries()).isEqualTo(8);
            softly.assertThat(configuration.conditionalCacheMaxBodyBytes()).isEqualTo(2048);
            softly.assertThat(configuration.conditionalCacheMaxBytes()).isEqualTo(65_536L);
            softly.assertThat(configuration.callObserver()).isSameAs(observer);
            softly.assertThat(configuration.allowInsecureHttp()).isTrue();
            softly.assertThat(configuration.flavor()).contains(GitLabFlavor.DEDICATED);
            softly.assertThat(configuration.tier()).contains(GitLabTier.ULTIMATE);
            softly.assertThat(configuration.pagination()).isEqualTo(pagination);
        });
    }

    @Test
    void build_shouldRefuse_whenMutationIntervalIsNegative() {
        final GitLabClientConfiguration.Builder builder =
                GitLabClientConfiguration.builder().mutationInterval(Duration.ofMillis(-1));

        final Throwable thrown = catchThrowable(builder::build);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mutationInterval");
    }

    @ParameterizedTest
    @ValueSource(ints = {101, 1000})
    void build_shouldRefuse_whenMaxConcurrentRequestsIsAboveHundred(final int maxConcurrentRequests) {
        final HttpTransportConfiguration transport = HttpTransportConfiguration.builder()
                .maxConcurrentRequests(maxConcurrentRequests)
                .build();
        final GitLabClientConfiguration.Builder builder =
                GitLabClientConfiguration.builder().transport(transport);

        final Throwable thrown = catchThrowable(builder::build);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maxConcurrentRequests");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void build_shouldRefuse_whenConditionalCacheLimitIsBelowOne(final int limit) {
        final GitLabClientConfiguration.Builder builder = GitLabClientConfiguration.builder()
                .conditionalCacheMaxEntries(limit)
                .conditionalCacheMaxBodyBytes(limit)
                .conditionalCacheMaxBytes(limit);

        final Throwable thrown = catchThrowable(builder::build);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("conditionalCacheMax");
    }

    @Test
    void retry_shouldRefuse_whenItBringsItsOwnRateLimitDetector() {
        final RateLimitDetector detector = (response, clock) -> Optional.empty();
        final RetryConfiguration custom = RetryConfiguration.defaults().withRateLimitDetector(detector);
        final GitLabClientConfiguration.Builder builder = GitLabClientConfiguration.builder();

        final Throwable thrown = catchThrowable(() -> builder.retry(custom));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rate-limit detector");
    }

    @Test
    void baseUri_shouldRefuse_whenNull() {
        final GitLabClientConfiguration.Builder builder = GitLabClientConfiguration.builder();

        final Throwable thrown = catchThrowable(() -> builder.baseUri(null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void retry_shouldKeepTheSettings_whenTheyUseTheDefaultRateLimitDetector() {
        final RetryConfiguration retry = RetryConfiguration.defaults();

        final GitLabClientConfiguration configuration =
                GitLabClientConfiguration.builder().retry(retry).build();

        assertThat(configuration.retry()).isSameAs(retry);
    }

    @Test
    void build_shouldRefuse_whenBaseUriHasAQuery() {
        final GitLabClientConfiguration.Builder builder =
                GitLabClientConfiguration.builder().baseUri(URI.create("https://gitlab.com?private_token=secret"));

        final Throwable thrown = catchThrowable(builder::build);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
    }

    @Test
    void build_shouldKeepTheRateLimitListener_whenOneIsSet() {
        final GitLabRateLimitListener listener = snapshot -> {};

        final GitLabClientConfiguration configuration =
                GitLabClientConfiguration.builder().rateLimitListener(listener).build();

        assertThat(configuration.rateLimitListener()).isSameAs(listener);
    }

    @Test
    void defaults_shouldHaveNoRateLimitListener_whenNothingIsSet() {
        assertThat(GitLabClientConfiguration.defaults().rateLimitListener()).isNull();
    }
}
