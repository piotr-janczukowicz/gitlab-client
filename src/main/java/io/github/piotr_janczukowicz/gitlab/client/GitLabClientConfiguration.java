package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireAtLeast;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireBetween;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.client.internal.GitLabApiBase;
import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitListener;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.client.HttpCallObserver;
import io.github.piotr_janczukowicz.http.transport.HttpTransportConfiguration;
import io.github.piotr_janczukowicz.http.transport.RetryConfiguration;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Settings of a {@link GitLabClient}: where the instance is, what the transport, the retry and the response cache may
 * do, and the flavor and tier of the instance. Build one with {@link #builder()}; {@link #defaults()} targets
 * gitlab.com.
 *
 * <p>An instance is immutable.
 */
public final class GitLabClientConfiguration {

    /** The instance root that a configuration targets unless it is told otherwise: gitlab.com. */
    public static final URI DEFAULT_BASE_URI = URI.create("https://gitlab.com");

    private static final String GITLAB_COM_TIER_MESSAGE =
            "a tier cannot be set for gitlab.com: a gitlab.com subscription belongs to a top-level group, so one token "
                    + "can reach groups of different tiers; set a tier only for a self-managed or Dedicated instance";
    private static final int MIN_CONCURRENT_REQUESTS = 1;
    private static final int MAX_CONCURRENT_REQUESTS = 100;
    private static final int DEFAULT_CACHE_ENTRIES = 64;
    private static final int DEFAULT_CACHE_BODY_BYTES = 1024 * 1024;
    private static final long DEFAULT_CACHE_BYTES = 4L * 1024 * 1024;

    private final URI baseUri;
    private final HttpHeaders defaultHeaders;
    private final HttpTransportConfiguration transport;
    private final RetryConfiguration retry;
    private final Duration mutationInterval;
    private final int conditionalCacheMaxEntries;
    private final int conditionalCacheMaxBodyBytes;
    private final long conditionalCacheMaxBytes;
    private final @Nullable GitLabRateLimitListener rateLimitListener;
    private final @Nullable HttpCallObserver callObserver;
    private final boolean isInsecureHttpAllowed;
    private final @Nullable GitLabFlavor flavor;
    private final @Nullable GitLabTier tier;
    private final PaginationConfiguration pagination;

    private GitLabClientConfiguration(
            final URI baseUri,
            final HttpHeaders defaultHeaders,
            final HttpTransportConfiguration transport,
            final RetryConfiguration retry,
            final Duration mutationInterval,
            final int conditionalCacheMaxEntries,
            final int conditionalCacheMaxBodyBytes,
            final long conditionalCacheMaxBytes,
            final @Nullable GitLabRateLimitListener rateLimitListener,
            final @Nullable HttpCallObserver callObserver,
            final boolean isInsecureHttpAllowed,
            final @Nullable GitLabFlavor flavor,
            final @Nullable GitLabTier tier,
            final PaginationConfiguration pagination) {
        this.baseUri = baseUri;
        this.defaultHeaders = defaultHeaders;
        this.transport = transport;
        this.retry = retry;
        this.mutationInterval = mutationInterval;
        this.conditionalCacheMaxEntries = conditionalCacheMaxEntries;
        this.conditionalCacheMaxBodyBytes = conditionalCacheMaxBodyBytes;
        this.conditionalCacheMaxBytes = conditionalCacheMaxBytes;
        this.rateLimitListener = rateLimitListener;
        this.callObserver = callObserver;
        this.isInsecureHttpAllowed = isInsecureHttpAllowed;
        this.flavor = flavor;
        this.tier = tier;
        this.pagination = pagination;
    }

    /**
     * A configuration with every default, targeting gitlab.com.
     *
     * @return the default configuration
     */
    public static GitLabClientConfiguration defaults() {
        return builder().build();
    }

    /**
     * Starts a configuration with every default, targeting gitlab.com.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Root URI of the instance, without {@code /api/v4}, which the client adds.
     *
     * @return the instance root
     */
    public URI baseUri() {
        return baseUri;
    }

    /**
     * Headers sent with every request, after {@code Accept: application/json} and before the headers of a call.
     *
     * @return the headers; never contains {@code User-Agent}, which is a client argument
     */
    public HttpHeaders defaultHeaders() {
        return defaultHeaders;
    }

    /**
     * Connection and concurrency limits of the transport.
     *
     * @return the transport settings
     */
    public HttpTransportConfiguration transport() {
        return transport;
    }

    /**
     * How a failed request is retried.
     *
     * @return the retry settings
     */
    public RetryConfiguration retry() {
        return retry;
    }

    /**
     * The least time between two requests that change state, or zero for no pacing.
     *
     * @return the interval
     */
    public Duration mutationInterval() {
        return mutationInterval;
    }

    /**
     * Most responses that the conditional cache keeps.
     *
     * @return the limit, at least one
     */
    public int conditionalCacheMaxEntries() {
        return conditionalCacheMaxEntries;
    }

    /**
     * Largest body that the conditional cache keeps.
     *
     * @return the limit in bytes, at least one
     */
    public int conditionalCacheMaxBodyBytes() {
        return conditionalCacheMaxBodyBytes;
    }

    /**
     * Most bytes of bodies that the conditional cache keeps in all.
     *
     * @return the limit in bytes, at least one
     */
    public long conditionalCacheMaxBytes() {
        return conditionalCacheMaxBytes;
    }

    /**
     * Listener of the rate-limit quota that responses report.
     *
     * @return the listener, or {@code null} for none
     */
    public @Nullable GitLabRateLimitListener rateLimitListener() {
        return rateLimitListener;
    }

    /**
     * Observer of every call.
     *
     * @return the observer, or {@code null} for none
     */
    public @Nullable HttpCallObserver callObserver() {
        return callObserver;
    }

    /**
     * Whether a plain {@code http} base URI to a remote host is allowed, which sends the credentials unencrypted.
     *
     * @return {@code true} if it is allowed; {@code false} by default
     */
    public boolean allowInsecureHttp() {
        return isInsecureHttpAllowed;
    }

    /**
     * The flavor that was set.
     *
     * @return the flavor, or empty if the client is to derive it from the base URI
     */
    public Optional<GitLabFlavor> flavor() {
        return Optional.ofNullable(flavor);
    }

    /**
     * The subscription tier of the instance that was set.
     *
     * @return the tier, or empty if it is not known, in which case no operation is refused for its tier
     */
    public Optional<GitLabTier> tier() {
        return Optional.ofNullable(tier);
    }

    /**
     * Page size and page limit of list calls.
     *
     * @return the pagination settings
     */
    public PaginationConfiguration pagination() {
        return pagination;
    }

    /** Builder of a {@link GitLabClientConfiguration}. */
    public static final class Builder {

        private URI baseUri = DEFAULT_BASE_URI;
        private HttpHeaders defaultHeaders = HttpHeaders.empty();
        private HttpTransportConfiguration transport = HttpTransportConfiguration.defaults();
        private RetryConfiguration retry = RetryConfiguration.defaults();
        private Duration mutationInterval = Duration.ZERO;
        private int conditionalCacheMaxEntries = DEFAULT_CACHE_ENTRIES;
        private int conditionalCacheMaxBodyBytes = DEFAULT_CACHE_BODY_BYTES;
        private long conditionalCacheMaxBytes = DEFAULT_CACHE_BYTES;
        private @Nullable GitLabRateLimitListener rateLimitListener;
        private @Nullable HttpCallObserver callObserver;
        private boolean isInsecureHttpAllowed;
        private @Nullable GitLabFlavor flavor;
        private @Nullable GitLabTier tier;
        private PaginationConfiguration pagination = PaginationConfiguration.defaults();

        private Builder() {}

        /**
         * Root URI of the instance, such as {@code https://gitlab.com} or {@code https://example.com/gitlab}.
         *
         * @param baseUri the instance root; the client adds {@code /api/v4}
         * @return this builder
         * @throws NullPointerException if {@code baseUri} is {@code null}
         */
        public Builder baseUri(final URI baseUri) {
            this.baseUri = requireNonNull(baseUri, "baseUri");
            return this;
        }

        /**
         * Headers to send with every request.
         *
         * @param defaultHeaders the headers; they must not set {@code User-Agent}, which is a client argument
         * @return this builder
         * @throws NullPointerException if {@code defaultHeaders} is {@code null}
         */
        public Builder defaultHeaders(final HttpHeaders defaultHeaders) {
            this.defaultHeaders = requireNonNull(defaultHeaders, "defaultHeaders");
            return this;
        }

        /**
         * Connection and concurrency limits of the transport.
         *
         * @param transport the settings; {@code maxConcurrentRequests} must be from 1 to 100
         * @return this builder
         * @throws NullPointerException if {@code transport} is {@code null}
         */
        public Builder transport(final HttpTransportConfiguration transport) {
            this.transport = requireNonNull(transport, "transport");
            return this;
        }

        /**
         * How a failed request is retried.
         *
         * @param retry the settings, with the default rate-limit detector: the client installs the one for GitLab
         * @return this builder
         * @throws NullPointerException if {@code retry} is {@code null}
         * @throws IllegalArgumentException if {@code retry} has a rate-limit detector of its own
         */
        public Builder retry(final RetryConfiguration retry) {
            requireNonNull(retry, "retry");
            if (!retry.hasDefaultRateLimitDetector()) {
                throw new IllegalArgumentException(
                        "retry must keep the default rate-limit detector: GitLab clients always use their own");
            }
            this.retry = retry;
            return this;
        }

        /**
         * The least time between two requests that change state.
         *
         * @param mutationInterval the interval; zero for no pacing, which is the default because GitLab documents no
         *     minimum
         * @return this builder
         * @throws NullPointerException if {@code mutationInterval} is {@code null}
         */
        public Builder mutationInterval(final Duration mutationInterval) {
            this.mutationInterval = requireNonNull(mutationInterval, "mutationInterval");
            return this;
        }

        /**
         * Most responses that the conditional cache keeps.
         *
         * @param conditionalCacheMaxEntries the limit, at least one
         * @return this builder
         */
        public Builder conditionalCacheMaxEntries(final int conditionalCacheMaxEntries) {
            this.conditionalCacheMaxEntries = conditionalCacheMaxEntries;
            return this;
        }

        /**
         * Largest body that the conditional cache keeps.
         *
         * @param conditionalCacheMaxBodyBytes the limit in bytes, at least one
         * @return this builder
         */
        public Builder conditionalCacheMaxBodyBytes(final int conditionalCacheMaxBodyBytes) {
            this.conditionalCacheMaxBodyBytes = conditionalCacheMaxBodyBytes;
            return this;
        }

        /**
         * Most bytes of bodies that the conditional cache keeps in all.
         *
         * @param conditionalCacheMaxBytes the limit in bytes, at least one
         * @return this builder
         */
        public Builder conditionalCacheMaxBytes(final long conditionalCacheMaxBytes) {
            this.conditionalCacheMaxBytes = conditionalCacheMaxBytes;
            return this;
        }

        /**
         * Listener of the rate-limit quota that responses report, called for every response that carries one.
         *
         * @param rateLimitListener the listener, or {@code null} for none
         * @return this builder
         */
        public Builder rateLimitListener(final @Nullable GitLabRateLimitListener rateLimitListener) {
            this.rateLimitListener = rateLimitListener;
            return this;
        }

        /**
         * Observer of every call.
         *
         * @param callObserver the observer, or {@code null} for none
         * @return this builder
         */
        public Builder callObserver(final @Nullable HttpCallObserver callObserver) {
            this.callObserver = callObserver;
            return this;
        }

        /**
         * Allows a plain {@code http} base URI to a remote host, which sends the credentials unencrypted. A loopback
         * host never needs it.
         *
         * @param allowInsecureHttp {@code true} to allow it
         * @return this builder
         */
        public Builder allowInsecureHttp(final boolean allowInsecureHttp) {
            this.isInsecureHttpAllowed = allowInsecureHttp;
            return this;
        }

        /**
         * The flavor of the instance, for one that the base URI does not reveal, such as GitLab Dedicated.
         *
         * @param flavor the flavor, or {@code null} to derive it from the base URI
         * @return this builder
         */
        public Builder flavor(final @Nullable GitLabFlavor flavor) {
            this.flavor = flavor;
            return this;
        }

        /**
         * The subscription tier of the instance.
         *
         * @param tier the tier, or {@code null} if it is not known
         * @return this builder
         */
        public Builder tier(final @Nullable GitLabTier tier) {
            this.tier = tier;
            return this;
        }

        /**
         * Page size and page limit of list calls.
         *
         * @param pagination the settings
         * @return this builder
         * @throws NullPointerException if {@code pagination} is {@code null}
         */
        public Builder pagination(final PaginationConfiguration pagination) {
            this.pagination = requireNonNull(pagination, "pagination");
            return this;
        }

        /**
         * Checks the settings and builds the configuration.
         *
         * @return the configuration
         * @throws IllegalArgumentException if the base URI is not an acceptable instance root, a tier is set for
         *     gitlab.com (the flavor, set or derived from the base URI, is {@link GitLabFlavor#GITLAB_COM}), the
         *     transport allows fewer than 1 or more than 100 concurrent requests, the mutation interval is negative, or
         *     a conditional cache limit is below one
         */
        public GitLabClientConfiguration build() {
            GitLabApiBase.of(baseUri);
            if (tier != null && effectiveFlavor() == GitLabFlavor.GITLAB_COM) {
                throw new IllegalArgumentException(GITLAB_COM_TIER_MESSAGE);
            }
            requireBetween(
                    transport.maxConcurrentRequests(),
                    MIN_CONCURRENT_REQUESTS,
                    MAX_CONCURRENT_REQUESTS,
                    "maxConcurrentRequests");
            requireAtLeast(mutationInterval, Duration.ZERO, "mutationInterval");
            requireAtLeast(conditionalCacheMaxEntries, 1, "conditionalCacheMaxEntries");
            requireAtLeast(conditionalCacheMaxBodyBytes, 1, "conditionalCacheMaxBodyBytes");
            requireAtLeast(conditionalCacheMaxBytes, 1L, "conditionalCacheMaxBytes");
            return new GitLabClientConfiguration(
                    baseUri,
                    defaultHeaders,
                    transport,
                    retry,
                    mutationInterval,
                    conditionalCacheMaxEntries,
                    conditionalCacheMaxBodyBytes,
                    conditionalCacheMaxBytes,
                    rateLimitListener,
                    callObserver,
                    isInsecureHttpAllowed,
                    flavor,
                    tier,
                    pagination);
        }

        private GitLabFlavor effectiveFlavor() {
            return flavor != null ? flavor : GitLabFlavor.of(baseUri);
        }
    }
}
