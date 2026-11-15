package io.github.piotr_janczukowicz.gitlab.client.internal;

import static io.github.piotr_janczukowicz.http.HttpHeaderNames.LOCATION;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabApiException;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.client.HttpErrorMapper;
import java.net.URI;
import java.time.Clock;
import org.jspecify.annotations.Nullable;

/**
 * Maps a failed response to the {@link GitLabApiException} of its status. It reads time from {@code clock}, the same
 * clock that stamps the rate-limit quota of the client.
 */
final class GitLabErrorMapper implements HttpErrorMapper {

    private final Clock clock;

    /**
     * Mapper that reads time from {@code clock}.
     *
     * @param clock time source of the rate-limit data
     * @throws NullPointerException if {@code clock} is {@code null}
     */
    GitLabErrorMapper(final Clock clock) {
        this.clock = requireNonNull(clock, "clock");
    }

    @Override
    public RuntimeException map(final HttpMethod method, final HttpResponse response, final URI requestUri) {
        return GitLabErrors.toException(method, response, requestUri, clock);
    }

    @Override
    public RuntimeException redirectFailure(
            final HttpResponse response,
            final String message,
            final @Nullable String rawBody,
            final @Nullable HttpMethod method,
            final URI uri) {
        return new GitLabApiException(
                response.statusCode(), message, rawBody, response.headers().without(LOCATION), method, uri);
    }
}
