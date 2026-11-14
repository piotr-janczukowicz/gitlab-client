package io.github.piotr_janczukowicz.gitlab.client.ratelimit;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireAtLeast;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.GitLabHeaders;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.util.StringUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The quota that a GitLab response reports, from its {@code RateLimit-*} headers (<a
 * href="https://docs.gitlab.com/administration/settings/user_and_ip_rate_limits/">user and IP rate limits</a>). GitLab
 * shows only the most restrictive throttle, named by {@code name}, and application limits such as issue creation do not
 * appear in it at all, so quota left here does not promise that a request is accepted. The window is not stated:
 * GitLab's period can differ from a minute, so read the end of the window from {@code resetAt}.
 *
 * @param limit the requests the client may send in the window
 * @param remaining the requests left in the window
 * @param resetAt when the quota is restored
 * @param name name of the throttle that the figures belong to, such as {@code throttle_authenticated_api}, or
 *     {@code null} if the response did not name it
 * @param observedAt when the response was received
 */
public record GitLabRateLimitSnapshot(
        int limit, int remaining, Instant resetAt, @Nullable String name, Instant observedAt) {

    public GitLabRateLimitSnapshot {
        requireAtLeast(limit, 0, "limit");
        requireAtLeast(remaining, 0, "remaining");
        requireNonNull(resetAt, "resetAt");
        requireNonNull(observedAt, "observedAt");
    }

    /**
     * Reads the quota of a response.
     *
     * @param headers headers of the response
     * @param observedAt when the response was received
     * @return the quota, or empty if the response lacks {@code RateLimit-Limit}, {@code RateLimit-Remaining} or
     *     {@code RateLimit-Reset}, or one of them is not a usable number; GitLab leaves them out of some responses
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Optional<GitLabRateLimitSnapshot> from(final HttpHeaders headers, final Instant observedAt) {
        requireNonNull(headers, "headers");
        requireNonNull(observedAt, "observedAt");
        final Integer limit = GitLabRateLimitHeaders.wholeNumber(headers, GitLabHeaders.RATE_LIMIT_LIMIT);
        final Integer remaining = GitLabRateLimitHeaders.wholeNumber(headers, GitLabHeaders.RATE_LIMIT_REMAINING);
        final Instant resetAt = GitLabRateLimitHeaders.epochSecond(headers, GitLabHeaders.RATE_LIMIT_RESET);
        if (limit == null || remaining == null || resetAt == null || limit < 0 || remaining < 0) {
            return Optional.empty();
        }
        final String name = StringUtils.stripToNull(
                headers.get(GitLabHeaders.RATE_LIMIT_NAME).orElse(null));
        return Optional.of(new GitLabRateLimitSnapshot(limit, remaining, resetAt, name, observedAt));
    }

    /**
     * Whether nothing is left in the window.
     *
     * @return {@code true} if {@code remaining} is zero
     */
    public boolean isExhausted() {
        return remaining == 0;
    }

    /**
     * Time until the quota is restored.
     *
     * @param now the current time
     * @return the time left, or zero if the reset has passed
     * @throws NullPointerException if {@code now} is {@code null}
     */
    public Duration untilReset(final Instant now) {
        requireNonNull(now, "now");
        final Duration left = Duration.between(now, resetAt);
        return left.isNegative() ? Duration.ZERO : left;
    }
}
