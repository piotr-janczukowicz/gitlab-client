package io.github.piotr_janczukowicz.gitlab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabApiException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabForbiddenException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabNotFoundException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabRateLimitException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabUnauthorizedException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabValidationError;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabValidationException;
import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.HttpErrorBodies;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabErrorsTest {

    private static final URI URI_OF_CALL = URI.create("https://gitlab.example.test/api/v4/projects/1");

    @ParameterizedTest
    @CsvSource({
        "401,GitLabUnauthorizedException",
        "403,GitLabForbiddenException",
        "404,GitLabNotFoundException",
        "429,GitLabRateLimitException",
        "422,GitLabValidationException",
        "400,GitLabApiException",
        "405,GitLabApiException",
        "409,GitLabApiException",
        "412,GitLabApiException",
        "500,GitLabApiException",
        "503,GitLabApiException"
    })
    void toException_shouldChooseTheTypeByStatus_whenBodyIsEmpty(final int status, final String type) {
        final HttpResponse response = response(status, "");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception.getClass().getSimpleName()).isEqualTo(type);
            softly.assertThat(exception.statusCode()).isEqualTo(status);
            softly.assertThat(exception).hasMessage("GitLab API request failed with HTTP " + status);
        });
    }

    @Test
    void toException_shouldChooseTheTypeByStatusNeverByText_whenTheMessageNamesAnotherStatus() {
        final HttpResponse response = response(404, "{\"message\":\"401 Unauthorized\"}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception).isInstanceOf(GitLabNotFoundException.class);
            softly.assertThat(exception).hasMessage("401 Unauthorized");
        });
    }

    @Test
    void toException_shouldUseTheMessage_whenBodyIsTheDocumentedUnauthorizedBody() {
        final HttpResponse response = response(401, "{\"message\":\"401 Unauthorized\"}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception).isInstanceOf(GitLabUnauthorizedException.class);
            softly.assertThat(exception).hasMessage("401 Unauthorized");
        });
    }

    @Test
    void toException_shouldBeValidationWithFieldErrors_whenA400BodyMapsFieldsToMessages() {
        final HttpResponse response = response(
                400, "{\"message\":{\"bio\":[\"is too long (maximum is 255 characters)\"],\"name\":[\"a\",\"b\"]}}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.POST, response, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabValidationException.class,
                        validation -> assertSoftly(softly -> {
                            softly.assertThat(validation.errors())
                                    .containsExactly(
                                            new GitLabValidationError(
                                                    "bio", List.of("is too long (maximum is 255 characters)")),
                                            new GitLabValidationError("name", List.of("a", "b")));
                            softly.assertThat(validation)
                                    .hasMessage("bio is too long (maximum is 255 characters); name a, b");
                            softly.assertThat(validation.statusCode()).isEqualTo(400);
                        }));
    }

    @Test
    void toException_shouldBeAGenericException_whenA400BodyHasOnlyAStringMessage() {
        final HttpResponse response = response(400, "{\"message\":\"400 (Bad request) \\\"title\\\" not given\"}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.POST, response, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception.getClass()).isEqualTo(GitLabApiException.class);
            softly.assertThat(exception).hasMessage("400 (Bad request) \"title\" not given");
        });
    }

    @Test
    void toException_shouldBeValidationWithTheBodyMessage_whenA422BodyHasAStringMessage() {
        final HttpResponse response = response(422, "{\"message\":\"Unprocessable entity\"}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.PUT, response, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabValidationException.class,
                        validation -> assertSoftly(softly -> {
                            softly.assertThat(validation.errors()).isEmpty();
                            softly.assertThat(validation).hasMessage("Unprocessable entity");
                        }));
    }

    @Test
    void toException_shouldBeValidationWithFieldErrors_whenA422BodyMapsFieldsToMessages() {
        final HttpResponse response = response(422, "{\"message\":{\"title\":[\"is blank\"]}}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.PUT, response, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabValidationException.class,
                        validation -> assertThat(validation.errors())
                                .containsExactly(new GitLabValidationError("title", List.of("is blank"))));
    }

    @Test
    void toException_shouldPreferTheMessageThenTheDescriptionThenTheError_whenSeveralArePresent() {
        final HttpResponse withDescription =
                response(401, "{\"error\":\"invalid_token\",\"error_description\":\"The access token expired\"}");
        final HttpResponse withErrorOnly = response(401, "{\"error\":\"invalid_token\"}");
        final HttpResponse withAll = response(
                401,
                "{\"message\":\"401 Unauthorized\",\"error\":\"invalid_token\",\"error_description\":\"expired\"}");

        final GitLabApiException description = GitLabErrors.toException(HttpMethod.GET, withDescription, URI_OF_CALL);
        final GitLabApiException error = GitLabErrors.toException(HttpMethod.GET, withErrorOnly, URI_OF_CALL);
        final GitLabApiException all = GitLabErrors.toException(HttpMethod.GET, withAll, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(description).hasMessage("The access token expired");
            softly.assertThat(error).hasMessage("invalid_token");
            softly.assertThat(all).hasMessage("401 Unauthorized");
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<html><body>502 Bad Gateway</body></html>",
                "plain text",
                "[\"a\"]",
                "\"text\"",
                "null",
                "{broken"
            })
    void toException_shouldUseTheStatusMessageAndKeepTheRawBody_whenBodyIsNotAnErrorObject(final String body) {
        final HttpResponse response = response(502, body);

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception).hasMessage("GitLab API request failed with HTTP 502");
            softly.assertThat(exception.rawBody()).contains(body);
        });
    }

    @Test
    void toException_shouldNotParseAHugeBody_whenItExceedsTheLimit() {
        final byte[] huge = new byte[GitLabErrors.MAX_JSON_BYTES + 1];
        Arrays.fill(huge, (byte) 'a');
        final HttpResponse response = new HttpResponse(500, HttpHeaders.empty(), HttpBody.of(huge));

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception).hasMessage("GitLab API request failed with HTTP 500");
            softly.assertThat(exception.rawBody().orElseThrow().length())
                    .isLessThanOrEqualTo(HttpErrorBodies.MAX_RAW_BODY_CHARS);
        });
    }

    @Test
    void toException_shouldUseTheStatusMessage_whenThereIsNoBody() {
        final HttpResponse response = new HttpResponse(500, HttpHeaders.empty(), HttpBody.absent());

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception).hasMessage("GitLab API request failed with HTTP 500");
            softly.assertThat(exception.rawBody()).isEmpty();
        });
    }

    @Test
    void toException_shouldKeepTheRequestAndTheHeaders_whenResponseFails() {
        final HttpResponse response = new HttpResponse(
                404,
                HttpHeaders.of("X-Request-Id", "req-1"),
                HttpBody.of("{\"message\":\"404 Project Not Found\"}".getBytes(StandardCharsets.UTF_8)));

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.DELETE, response, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception.method()).contains(HttpMethod.DELETE);
            softly.assertThat(exception.uri()).contains(URI_OF_CALL);
            softly.assertThat(exception.headers().get("X-Request-Id")).contains("req-1");
            softly.assertThat(exception.rawBody()).contains("{\"message\":\"404 Project Not Found\"}");
        });
    }

    @Test
    void toException_shouldRefuse_whenAnArgumentIsNull() {
        final HttpResponse response = response(500, "");

        final Throwable thrown = catchThrowable(() -> GitLabErrors.toException(null, response, URI_OF_CALL));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabErrors> constructor = GitLabErrors.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toException_shouldBeForbidden_whenA403BodyIsGiven() {
        final HttpResponse response = response(403, "{\"message\":\"403 Forbidden\"}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.DELETE, response, URI_OF_CALL);

        assertThat(exception).isInstanceOf(GitLabForbiddenException.class).hasMessage("403 Forbidden");
    }

    @Test
    void toException_shouldBeRateLimited_whenA429BodyIsGiven() {
        final HttpResponse response = new HttpResponse(
                429, HttpHeaders.of("Retry-After", "30"), HttpBody.of("Retry later".getBytes(StandardCharsets.UTF_8)));

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception).isInstanceOf(GitLabRateLimitException.class);
    }

    private static HttpResponse response(final int status, final String body) {
        return new HttpResponse(status, HttpHeaders.empty(), HttpBody.of(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void toException_shouldUseTheStatusMessage_whenBodyIsAnEmptyObject() {
        final HttpResponse response = response(401, "{}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception).hasMessage("GitLab API request failed with HTTP 401");
    }

    @Test
    void toException_shouldSkipABlankMessage_whenTheBodyHasAnOAuthError() {
        final HttpResponse response = response(401, "{\"message\":\"   \",\"error\":\"invalid_token\"}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception).hasMessage("invalid_token");
    }

    @Test
    void toException_shouldSkipABlankDescription_whenTheBodyHasAnError() {
        final HttpResponse response = response(401, "{\"error\":\"invalid_token\",\"error_description\":\" \"}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception).hasMessage("invalid_token");
    }

    @Test
    void toException_shouldBeAGenericException_whenA409BodyMapsFieldsToMessages() {
        final HttpResponse response = response(409, "{\"message\":{\"path\":[\"has already been taken\"]}}");

        final GitLabApiException exception = GitLabErrors.toException(HttpMethod.POST, response, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception.getClass()).isEqualTo(GitLabApiException.class);
            softly.assertThat(exception).hasMessage("path has already been taken");
        });
    }
}
