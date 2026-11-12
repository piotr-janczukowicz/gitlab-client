package io.github.piotr_janczukowicz.gitlab.client.exception;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpMethod;
import java.io.Serial;
import java.net.URI;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * GitLab refused the request because of its content: a 422, or a 400 whose body names the fields at fault. A 400 for a
 * missing attribute has a single text message and is a plain {@link GitLabApiException}.
 */
public final class GitLabValidationException extends GitLabApiException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<GitLabValidationError> errors;

    /**
     * Describes a failed response.
     *
     * @param statusCode HTTP status of the response
     * @param message what went wrong, from the body or the status; cut to a readable length
     * @param rawBody the body as text, cut to a limit, or {@code null} if there was none
     * @param headers headers of the response; secrets in them are hidden when the exception is printed
     * @param method method of the request, or {@code null} if unknown
     * @param uri URI of the request, or {@code null} if unknown
     * @param errors the fields at fault; empty if the body named none, as for a 422 with a text message
     */
    public GitLabValidationException(
            final int statusCode,
            final String message,
            final @Nullable String rawBody,
            final List<GitLabValidationError> errors,
            final HttpHeaders headers,
            final @Nullable HttpMethod method,
            final @Nullable URI uri) {
        super(statusCode, message, rawBody, headers, method, uri);
        this.errors = List.copyOf(errors);
    }

    /**
     * The fields at fault.
     *
     * @return the errors, in the order of the body; a copy that cannot be changed
     */
    public List<GitLabValidationError> errors() {
        return errors;
    }
}
