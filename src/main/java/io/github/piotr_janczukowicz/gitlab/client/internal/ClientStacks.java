package io.github.piotr_janczukowicz.gitlab.client.internal;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.GitLabHeaders;
import io.github.piotr_janczukowicz.gitlab.client.GitLabClientConfiguration;
import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitDetector;
import io.github.piotr_janczukowicz.http.HttpHeaderNames;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProvider;
import io.github.piotr_janczukowicz.http.transport.ConcurrencyLimitingHttpTransport;
import io.github.piotr_janczukowicz.http.transport.ConditionalCachingHttpTransport;
import io.github.piotr_janczukowicz.http.transport.HttpTransport;
import io.github.piotr_janczukowicz.http.transport.HttpTransportConfiguration;
import io.github.piotr_janczukowicz.http.transport.MutationPacingHttpTransport;
import io.github.piotr_janczukowicz.http.transport.RetryConfiguration;
import java.time.Duration;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Builds the transport stack of a GitLab client over a base transport. Outermost first: the conditional cache, retry
 * with rate-limit waits, pacing of requests that change state (only when the interval is not zero), a limit on the
 * requests in flight, and the base transport.
 */
public final class ClientStacks {

    // A response depends on the representation that Accept asks for, and on the user that an administrator acts as
    // with Sudo: both key the cache, so that one user's response is never replayed for another.
    private static final Set<String> CONDITIONAL_CACHE_VARYING_HEADERS =
            Set.of(HttpHeaderNames.ACCEPT, GitLabHeaders.SUDO);

    private ClientStacks() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Stacks the decorators over {@code base} and starts the client that sends through them.
     *
     * @param base the transport that reaches the network; closed if the client is refused
     * @param auth source of the credentials
     * @param userAgent the {@code User-Agent} of every request
     * @param configuration the settings
     * @param retrying adds retry to a transport; {@code RetryingHttpTransport::new}, or a test double
     * @return a client builder, with the user agent set
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code userAgent} is not visible ASCII
     */
    public static DefaultGitLabClient.Builder assemble(
            final HttpTransport base,
            final AuthenticationProvider auth,
            final String userAgent,
            final GitLabClientConfiguration configuration,
            final BiFunction<HttpTransport, RetryConfiguration, HttpTransport> retrying) {
        try {
            final HttpTransportConfiguration transport = configuration.transport();
            final HttpTransport retried = retrying.apply(
                    paced(
                            new ConcurrencyLimitingHttpTransport(
                                    base, transport.maxConcurrentRequests(), transport.maxSlotWait()),
                            configuration.mutationInterval()),
                    configuration.retry().withRateLimitDetector(GitLabRateLimitDetector.INSTANCE));
            final HttpTransport stack = new ConditionalCachingHttpTransport(
                    retried,
                    configuration.conditionalCacheMaxEntries(),
                    configuration.conditionalCacheMaxBodyBytes(),
                    configuration.conditionalCacheMaxBytes(),
                    CONDITIONAL_CACHE_VARYING_HEADERS);
            return DefaultGitLabClient.builder(stack, requireNonNull(configuration, "configuration"), auth)
                    .userAgent(userAgent);
        } catch (final RuntimeException rejected) {
            base.close();
            throw rejected;
        }
    }

    private static HttpTransport paced(final HttpTransport delegate, final Duration mutationInterval) {
        if (mutationInterval.isZero()) {
            return delegate;
        }
        return new MutationPacingHttpTransport(
                delegate, mutationInterval, MutationPacingHttpTransport.DEFAULT_MUTATION_METHODS);
    }
}
