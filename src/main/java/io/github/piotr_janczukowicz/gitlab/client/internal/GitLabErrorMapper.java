package io.github.piotr_janczukowicz.gitlab.client.internal;

import static io.github.piotr_janczukowicz.http.HttpHeaderNames.LOCATION;

import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabApiException;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.client.HttpErrorMapper;
import java.net.URI;
import org.jspecify.annotations.Nullable;

/** Maps a failed response to the {@link GitLabApiException} of its status. */
enum GitLabErrorMapper implements HttpErrorMapper {

    /** The only instance: the status of a response is all that the mapper needs. */
    INSTANCE;

    @Override
    public RuntimeException map(final HttpMethod method, final HttpResponse response, final URI requestUri) {
        return GitLabErrors.toException(method, response, requestUri);
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
