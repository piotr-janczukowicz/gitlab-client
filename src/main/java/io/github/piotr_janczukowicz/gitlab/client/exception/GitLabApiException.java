package io.github.piotr_janczukowicz.gitlab.client.exception;

import io.github.piotr_janczukowicz.http.HttpErrorBodies;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.client.HttpException;
import java.io.Serial;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/**
 * A request that GitLab answered with an error status. The type follows the status, never the text of the message,
 * which GitLab may change: {@link GitLabUnauthorizedException} for 401, {@link GitLabForbiddenException} for 403,
 * {@link GitLabNotFoundException} for 404, {@link GitLabRateLimitException} for 429 and
 * {@link GitLabValidationException} for a 422, or a 400 that names the fields at fault. Any other status is this type
 * itself.
 */
public sealed class GitLabApiException extends HttpException
        permits GitLabForbiddenException,
                GitLabNotFoundException,
                GitLabRateLimitException,
                GitLabUnauthorizedException,
                GitLabValidationException {

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
    public GitLabApiException(
            final int statusCode,
            final String message,
            final @Nullable String rawBody,
            final HttpHeaders headers,
            final @Nullable HttpMethod method,
            final @Nullable URI uri) {
        super(statusCode, HttpErrorBodies.truncated(message), rawBody, headers, method, uri);
    }
}
