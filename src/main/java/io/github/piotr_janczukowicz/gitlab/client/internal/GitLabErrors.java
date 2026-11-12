package io.github.piotr_janczukowicz.gitlab.client.internal;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.GitLabErrorBody;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabApiException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabForbiddenException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabNotFoundException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabRateLimitException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabUnauthorizedException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabValidationError;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabValidationException;
import io.github.piotr_janczukowicz.gitlab.json.GitLabJsonCodecs;
import io.github.piotr_janczukowicz.http.HttpErrorBodies;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.json.JsonCodec;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a failed GitLab response into the exception of its status. The status decides the type and never the message,
 * which GitLab may reword. The body only supplies the message, the fields at fault, and the raw text.
 *
 * <p>The statuses are those of <a href="https://docs.gitlab.com/api/rest/troubleshooting/">troubleshooting</a>:
 * validation failures are a 400 whose {@code message} maps fields to their messages, so a 400 with such a map, and a
 * 422, are {@link GitLabValidationException}; a 400 for a missing attribute has a text message and stays a plain
 * {@link GitLabApiException}.
 */
final class GitLabErrors {

    static final int MAX_JSON_BYTES = 1_048_576;

    private static final Logger LOG = LoggerFactory.getLogger(GitLabErrors.class);

    private static final JsonCodec CODEC = GitLabJsonCodecs.defaults();

    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;
    private static final int TOO_MANY_REQUESTS = 429;

    private GitLabErrors() {
        throw new UnsupportedOperationException("Utility class");
    }

    static GitLabApiException toException(final HttpMethod method, final HttpResponse response, final URI requestUri) {
        requireNonNull(method, "method");
        requireNonNull(response, "response");
        requireNonNull(requestUri, "requestUri");

        final int status = response.statusCode();
        final ParsedError parsed =
                response.mapBody(bytes -> parse(bytes, status, response.charset()), () -> absent(status));
        final Failure failure = new Failure(response, parsed, method, requestUri);
        return switch (status) {
            case UNAUTHORIZED -> failure.unauthorized();
            case FORBIDDEN -> failure.forbidden();
            case NOT_FOUND -> failure.notFound();
            case TOO_MANY_REQUESTS -> failure.rateLimited();
            case UNPROCESSABLE -> failure.invalid();
            case BAD_REQUEST -> parsed.errors().isEmpty() ? failure.generic() : failure.invalid();
            default -> failure.generic();
        };
    }

    private static ParsedError absent(final int status) {
        return new ParsedError(fallbackMessage(status), List.of(), null);
    }

    private static ParsedError parse(final byte[] bytes, final int status, final Charset charset) {
        final String rawBody = HttpErrorBodies.decodeBounded(bytes, charset);
        if (bytes.length == 0 || bytes.length > MAX_JSON_BYTES) {
            LOG.atDebug()
                    .setMessage("HTTP {} error body of {} bytes is not parsed as a GitLab error object")
                    .addArgument(status)
                    .addArgument(bytes.length)
                    .log();
            return new ParsedError(fallbackMessage(status), List.of(), rawBody);
        }
        try {
            return fromBody(CODEC.decode(bytes, GitLabErrorBody.class), status, rawBody);
        } catch (final RuntimeException unused) {
            LOG.atDebug()
                    .setMessage("HTTP {} error body of {} bytes is not a GitLab error object ({}); using the status")
                    .addArgument(status)
                    .addArgument(bytes.length)
                    .addArgument(unused.getClass().getName())
                    .log();
            return new ParsedError(fallbackMessage(status), List.of(), rawBody);
        }
    }

    private static ParsedError fromBody(final GitLabErrorBody body, final int status, final String rawBody) {
        final List<GitLabValidationError> errors = body.fieldErrors().stream()
                .map(field -> new GitLabValidationError(
                        withoutControlCharacters(field.field()),
                        field.messages().stream()
                                .map(GitLabErrors::withoutControlCharacters)
                                .toList()))
                .toList();
        return new ParsedError(withoutControlCharacters(messageOf(body, errors, status)), errors, rawBody);
    }

    // Server text reaches logs through the exception message, so a control character that could forge a log line or
    // an escape sequence becomes a space. The raw body keeps the text as it came.
    private static String withoutControlCharacters(final String text) {
        final StringBuilder clean = new StringBuilder(text.length());
        text.chars().forEach(c -> clean.append(Character.isISOControl(c) ? ' ' : (char) c));
        return clean.toString();
    }

    private static String messageOf(
            final GitLabErrorBody body, final List<GitLabValidationError> errors, final int status) {
        if (!isBlank(body.message())) {
            return body.message();
        }
        if (!errors.isEmpty()) {
            return errors.stream().map(GitLabErrors::describe).collect(Collectors.joining("; "));
        }
        if (!isBlank(body.errorDescription())) {
            return body.errorDescription();
        }
        return isBlank(body.error()) ? fallbackMessage(status) : body.error();
    }

    private static String describe(final GitLabValidationError error) {
        return error.field() + " " + String.join(", ", error.messages());
    }

    private static boolean isBlank(final @Nullable String text) {
        return text == null || text.isBlank();
    }

    private static String fallbackMessage(final int status) {
        return "GitLab API request failed with HTTP " + status;
    }

    private record ParsedError(
            String message,
            List<GitLabValidationError> errors,
            @Nullable String rawBody) {}

    private record Failure(HttpResponse response, ParsedError parsed, HttpMethod method, URI uri) {

        GitLabApiException generic() {
            return new GitLabApiException(
                    response.statusCode(), parsed.message(), parsed.rawBody(), response.headers(), method, uri);
        }

        GitLabApiException unauthorized() {
            return new GitLabUnauthorizedException(
                    response.statusCode(), parsed.message(), parsed.rawBody(), response.headers(), method, uri);
        }

        GitLabApiException forbidden() {
            return new GitLabForbiddenException(
                    response.statusCode(), parsed.message(), parsed.rawBody(), response.headers(), method, uri);
        }

        GitLabApiException notFound() {
            return new GitLabNotFoundException(
                    response.statusCode(), parsed.message(), parsed.rawBody(), response.headers(), method, uri);
        }

        GitLabApiException rateLimited() {
            return new GitLabRateLimitException(
                    response.statusCode(), parsed.message(), parsed.rawBody(), response.headers(), method, uri);
        }

        GitLabApiException invalid() {
            return new GitLabValidationException(
                    response.statusCode(),
                    parsed.message(),
                    parsed.rawBody(),
                    parsed.errors(),
                    response.headers(),
                    method,
                    uri);
        }
    }
}
