package io.github.piotr_janczukowicz.gitlab.client.exception;

import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitSnapshot;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.transport.RateLimitGateHeaders;
import java.io.Serial;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * GitLab answered 429 and the client gave up waiting. The rate-limit headers of the answer are in {@link #headers()},
 * and what the client read from them is in {@link #rateLimit()} and {@link #retryAt()}.
 */
public final class GitLabRateLimitException extends GitLabApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final @Nullable GitLabRateLimitSnapshot rateLimit;
    private final @Nullable Instant retryAt;

    /**
     * Describes a failed response.
     *
     * @param statusCode HTTP status of the response
     * @param message what went wrong, from the body or the status; cut to a readable length
     * @param rawBody the body as text, cut to a limit, or {@code null} if there was none
     * @param rateLimit the quota that the rate-limit headers of the response describe, or {@code null} if they are
     *     missing or unusable
     * @param retryAt when GitLab will accept a request again, or {@code null} if the response does not say
     * @param headers headers of the response; secrets in them are hidden when the exception is printed
     * @param method method of the request, or {@code null} if unknown
     * @param uri URI of the request, or {@code null} if unknown
     */
    public GitLabRateLimitException(
            final int statusCode,
            final String message,
            final @Nullable String rawBody,
            final @Nullable GitLabRateLimitSnapshot rateLimit,
            final @Nullable Instant retryAt,
            final HttpHeaders headers,
            final @Nullable HttpMethod method,
            final @Nullable URI uri) {
        super(statusCode, message, rawBody, headers, method, uri);
        this.rateLimit = rateLimit;
        this.retryAt = retryAt;
    }

    /**
     * The quota that the rate-limit headers of the response describe: the limit, what is left, when it resets and the
     * name of the throttle.
     *
     * @return the quota, or empty if the response lacks {@code RateLimit-Limit}, {@code RateLimit-Remaining} or
     *     {@code RateLimit-Reset}, or one of them is not usable
     */
    public Optional<GitLabRateLimitSnapshot> rateLimit() {
        return Optional.ofNullable(rateLimit);
    }

    /**
     * When GitLab will accept a request again: {@code Retry-After} counted from the moment the response arrived, or,
     * without it, the reset time of {@link #rateLimit()}.
     *
     * @return the moment, or empty if the response gives neither
     */
    public Optional<Instant> retryAt() {
        return Optional.ofNullable(retryAt);
    }

    /**
     * The name of the throttle that refused the request ({@code RateLimit-Name}), such as
     * {@code throttle_authenticated_api}.
     *
     * @return the name, or empty if GitLab did not send one
     */
    public Optional<String> throttle() {
        return rateLimit().map(GitLabRateLimitSnapshot::name);
    }

    /**
     * Whether the client refused to send the request because an earlier response reported a limit that is still in
     * force. The rate-limit data of this exception is then that of the earlier rejection, and no request reached
     * GitLab.
     *
     * @return {@code true} if the response behind this exception was replayed by the client, {@code false} if GitLab
     *     sent it
     */
    public boolean isClientSideBlock() {
        return RateLimitGateHeaders.isPresent(headers());
    }
}
