package io.github.piotr_janczukowicz.gitlab.client.ratelimit;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.GitLabHeaders;
import io.github.piotr_janczukowicz.http.HttpRequest;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.HttpStatus;
import io.github.piotr_janczukowicz.http.transport.RateLimitDetector;
import io.github.piotr_janczukowicz.http.transport.RateLimitSignal;
import io.github.piotr_janczukowicz.util.ValidationUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Tells the retry layer when GitLab rate-limited a request, from the documented rules (<a
 * href="https://docs.gitlab.com/user/gitlab_com/rate_limits/">GitLab.com rate limits</a>, <a
 * href="https://docs.gitlab.com/administration/settings/user_and_ip_rate_limits/">user and IP rate limits</a>).
 *
 * <ul>
 *   <li>A response is rate-limited when its status is 429, never because of its text: the body is configurable, and no
 *       other status is a rate limit, so a 403 is never one.
 *   <li>{@code Retry-After}, the seconds left until the quota is restored, is the wait that the server asks for. When
 *       it is missing or not a positive number, the retry layer waits its own default and doubles it per limit.
 *   <li>When {@code RateLimit-Remaining} is zero, the throttle that the headers describe is the one that fired. The
 *       limit holds back every request of the credential until {@code RateLimit-Reset}, a Unix time.
 *   <li>Otherwise the 429 is an application limit, such as issue creation, that the headers do not describe, and they
 *       say nothing about when it ends. It holds back only requests with the same method and path, so a limit of a day
 *       on creating projects does not stop reads. A reset time that belongs to another throttle is not used.
 *   <li>A successful response with {@code RateLimit-Remaining} zero and a reset still ahead holds the next requests of
 *       the credential until the reset, as GitLab advises to slow down before the quota is used up.
 * </ul>
 */
public enum GitLabRateLimitDetector implements RateLimitDetector {

    /** The only instance. */
    INSTANCE;

    private static final String RESPONSE_PARAMETER = "response";
    private static final String REQUEST_PARAMETER = "request";
    private static final String CLOCK_PARAMETER = "clock";
    private static final int FIRST_SUCCESS = 200;
    private static final int FIRST_ERROR = 400;

    @Override
    public Optional<RateLimitSignal> detect(final HttpResponse response, final Clock clock) {
        requireNonNull(response, RESPONSE_PARAMETER);
        requireNonNull(clock, CLOCK_PARAMETER);
        return signal(response, clock, null);
    }

    @Override
    public Optional<RateLimitSignal> detect(final HttpResponse response, final HttpRequest request, final Clock clock) {
        requireNonNull(response, RESPONSE_PARAMETER);
        requireNonNull(request, REQUEST_PARAMETER);
        requireNonNull(clock, CLOCK_PARAMETER);
        return signal(response, clock, bucketOf(request));
    }

    @Override
    public Optional<RateLimitSignal> detectExhaustion(
            final HttpResponse response, final HttpRequest request, final Clock clock) {
        requireNonNull(response, RESPONSE_PARAMETER);
        requireNonNull(request, REQUEST_PARAMETER);
        requireNonNull(clock, CLOCK_PARAMETER);
        final int status = response.statusCode();
        if (status < FIRST_SUCCESS || status >= FIRST_ERROR || !isExhausted(response)) {
            return Optional.empty();
        }
        return Optional.ofNullable(resetAfter(response, clock))
                .map(resetAt -> new RateLimitSignal(null, resetAt, null));
    }

    /**
     * The quota that a request counts against when GitLab names none: requests with the same method and path.
     *
     * @param request the request
     * @return the method and the path, without the query
     */
    @Override
    public String bucketOf(final HttpRequest request) {
        requireNonNull(request, REQUEST_PARAMETER);
        return request.method().name() + " " + request.encodedPath();
    }

    private static Optional<RateLimitSignal> signal(
            final HttpResponse response, final Clock clock, final @Nullable String scopedBucket) {
        if (!response.hasStatus(HttpStatus.TOO_MANY_REQUESTS)) {
            return Optional.empty();
        }
        final Duration retryAfter =
                response.retryAfter(clock).filter(ValidationUtils::isPositive).orElse(null);
        if (isExhausted(response)) {
            return Optional.of(new RateLimitSignal(retryAfter, resetAfter(response, clock), null));
        }
        return Optional.of(new RateLimitSignal(retryAfter, null, scopedBucket));
    }

    private static boolean isExhausted(final HttpResponse response) {
        final Integer remaining =
                GitLabRateLimitHeaders.wholeNumber(response.headers(), GitLabHeaders.RATE_LIMIT_REMAINING);
        return remaining != null && remaining == 0;
    }

    private static @Nullable Instant resetAfter(final HttpResponse response, final Clock clock) {
        final Instant serverNow = response.serverDate().orElseGet(clock::instant);
        final Instant resetAt = GitLabRateLimitHeaders.epochSecond(response.headers(), GitLabHeaders.RATE_LIMIT_RESET);
        return resetAt != null && resetAt.isAfter(serverNow) ? resetAt : null;
    }
}
