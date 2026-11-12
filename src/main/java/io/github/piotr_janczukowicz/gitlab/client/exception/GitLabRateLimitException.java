package io.github.piotr_janczukowicz.gitlab.client.exception;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpMethod;
import java.io.Serial;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * GitLab answered 429 and the client gave up waiting. The rate-limit headers of the answer are in {@link #headers()}.
 */
public final class GitLabRateLimitException extends GitLabApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Describes a failed response.
     *
     * @param statusCode HTTP status of the response
     * @param message what went wrong, from the body or the status; cut to a readable length
     * @param rawBody the body as text, cut to a limit, or {@code null} if there was none
     * @param headers headers of the response; secrets in them are hidden when the exception is printed
     * @param method method of the request, or {@code null} if unknown
     * @param uri URI of the request, or {@code null} if unknown
     */
    public GitLabRateLimitException(
            final int statusCode,
            final String message,
            final @Nullable String rawBody,
            final HttpHeaders headers,
            final @Nullable HttpMethod method,
            final @Nullable URI uri) {
        super(statusCode, message, rawBody, headers, method, uri);
    }
}
