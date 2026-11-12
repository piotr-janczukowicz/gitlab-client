package io.github.piotr_janczukowicz.gitlab.client.internal;

import static io.github.piotr_janczukowicz.http.HttpHeaderNames.ACCEPT;
import static io.github.piotr_janczukowicz.http.HttpHeaderNames.USER_AGENT;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.client.GitLabClient;
import io.github.piotr_janczukowicz.gitlab.client.GitLabClientConfiguration;
import io.github.piotr_janczukowicz.gitlab.client.GitLabFlavor;
import io.github.piotr_janczukowicz.gitlab.client.GitLabTier;
import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitSnapshot;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpUserAgents;
import io.github.piotr_janczukowicz.http.client.HttpClient;
import io.github.piotr_janczukowicz.http.client.HttpClientConfiguration;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProvider;
import io.github.piotr_janczukowicz.http.transport.HttpTransport;
import io.github.piotr_janczukowicz.http.uri.HttpUris;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link GitLabClient} over a {@link HttpClient}: it resolves request paths against the API root of the instance
 * and sends {@code Accept} and {@code User-Agent} with every request. Create it through
 * {@link io.github.piotr_janczukowicz.gitlab.client.GitLabClients}; this type is not part of the supported API.
 */
public final class DefaultGitLabClient extends HttpClient implements GitLabClient {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultGitLabClient.class);

    private static final String JSON = "application/json";

    private final PaginationConfiguration pagination;
    private final GitLabFlavor flavor;
    private final @Nullable GitLabTier tier;
    private final GitLabRateLimitTracker rateLimits;

    private DefaultGitLabClient(
            final HttpClient.Builder http,
            final GitLabClientConfiguration configuration,
            final GitLabRateLimitTracker rateLimits) {
        super(http);
        this.rateLimits = rateLimits;
        this.pagination = configuration.pagination();
        this.flavor = configuration.flavor().orElseGet(() -> GitLabFlavor.of(configuration.baseUri()));
        this.tier = configuration.tier().orElse(null);
    }

    /**
     * Starts a client.
     *
     * @param transport the transport stack the client sends through; the client closes it
     * @param configuration the settings
     * @param auth source of the credentials
     * @return a builder that still needs the user agent
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Builder builder(
            final HttpTransport transport,
            final GitLabClientConfiguration configuration,
            final AuthenticationProvider auth) {
        return new Builder(transport, configuration, auth);
    }

    @Override
    public GitLabFlavor flavor() {
        return flavor;
    }

    @Override
    public Optional<GitLabTier> tier() {
        return Optional.ofNullable(tier);
    }

    @Override
    public Optional<GitLabRateLimitSnapshot> lastRateLimit() {
        return rateLimits.last();
    }

    @Override
    public PaginationConfiguration pagination() {
        return pagination;
    }

    /** Builder of a {@link DefaultGitLabClient}. */
    public static final class Builder {

        private final HttpTransport transport;
        private final GitLabClientConfiguration configuration;
        private final AuthenticationProvider auth;
        private @Nullable String userAgent;

        private Builder(
                final HttpTransport transport,
                final GitLabClientConfiguration configuration,
                final AuthenticationProvider auth) {
            this.transport = requireNonNull(transport, "transport");
            this.configuration = requireNonNull(configuration, "configuration");
            this.auth = requireNonNull(auth, "auth");
        }

        /**
         * The {@code User-Agent} of every request.
         *
         * @param userAgent the value; visible ASCII only, not blank
         * @return this builder
         * @throws NullPointerException if {@code userAgent} is {@code null}
         * @throws IllegalArgumentException if it is blank or holds a character that is not visible ASCII
         */
        public Builder userAgent(final String userAgent) {
            this.userAgent = HttpUserAgents.requireValid(userAgent);
            return this;
        }

        /**
         * Builds the client. If that fails, the transport is closed, because no client owns it yet.
         *
         * @return the client
         * @throws NullPointerException if no user agent was set
         * @throws IllegalArgumentException if the default headers set {@code User-Agent}
         * @throws IllegalStateException if the base URI is plain {@code http} to a remote host and that is not allowed
         */
        public DefaultGitLabClient build() {
            try {
                final String agent = requireNonNull(userAgent, "userAgent");
                requireNoUserAgentDefault();
                requireSecureOrAllowed(configuration.baseUri());
                final HttpClientConfiguration http = new HttpClientConfiguration(
                        GitLabApiBase.of(configuration.baseUri()).apiUri(), headers(agent));
                final GitLabRateLimitTracker rateLimits =
                        new GitLabRateLimitTracker(configuration.rateLimitListener(), Clock.systemUTC());
                final HttpClient.Builder builder = HttpClient.builder(transport, http)
                        .auth(auth)
                        .errorMapper(GitLabErrorMapper.INSTANCE)
                        .responseHeadersObserver(rateLimits::observe);
                if (configuration.callObserver() != null) {
                    builder.callObserver(configuration.callObserver());
                }
                return new DefaultGitLabClient(builder, configuration, rateLimits);
            } catch (final RuntimeException failed) {
                transport.close();
                throw failed;
            }
        }

        private void requireNoUserAgentDefault() {
            if (configuration.defaultHeaders().get(USER_AGENT).isPresent()) {
                throw new IllegalArgumentException(
                        "defaultHeaders must not set " + USER_AGENT + ": it is an argument of the client");
            }
        }

        private HttpHeaders headers(final String agent) {
            return HttpHeaders.of(ACCEPT, JSON, USER_AGENT, agent).merge(configuration.defaultHeaders());
        }

        private void requireSecureOrAllowed(final URI baseUri) {
            if (!"http".equalsIgnoreCase(baseUri.getScheme()) || HttpUris.isLoopbackHost(baseUri.getHost())) {
                return;
            }
            if (!configuration.allowInsecureHttp()) {
                throw new IllegalStateException("GitLab base URI " + HttpUris.describeSafe(baseUri)
                        + " is plain http, which would send the credentials unencrypted;"
                        + " use https, or call allowInsecureHttp(true) for a trusted network");
            }
            LOG.atWarn()
                    .setMessage("GitLab base URI {} is plain http: the credentials are sent unencrypted")
                    .addArgument(HttpUris.describeSafe(baseUri))
                    .log();
        }
    }
}
