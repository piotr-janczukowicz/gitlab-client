package io.github.piotr_janczukowicz.gitlab.client.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.groups.Tuple.tuple;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.client.HttpException;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabApiExceptionTest {

    private static final URI URI_OF_CALL = URI.create("https://gitlab.example.test/api/v4/projects/1");

    @Test
    void constructor_shouldKeepTheFailureOfTheResponse_whenCreated() {
        final HttpHeaders headers = HttpHeaders.of("X-Request-Id", "req-1");

        final GitLabApiException exception =
                new GitLabApiException(500, "boom", "raw", headers, HttpMethod.POST, URI_OF_CALL);

        assertSoftly(softly -> {
            softly.assertThat(exception).isInstanceOf(HttpException.class).hasMessage("boom");
            softly.assertThat(exception.statusCode()).isEqualTo(500);
            softly.assertThat(exception.rawBody()).contains("raw");
            softly.assertThat(exception.headers().get("X-Request-Id")).contains("req-1");
            softly.assertThat(exception.method()).contains(HttpMethod.POST);
            softly.assertThat(exception.uri()).contains(URI_OF_CALL);
        });
    }

    @Test
    void constructor_shouldCutALongMessage_whenItExceedsTheLimit() {
        final String message = "x".repeat(5000);

        final GitLabApiException exception =
                new GitLabApiException(500, message, null, HttpHeaders.empty(), null, null);

        assertThat(exception.getMessage().length()).isLessThan(message.length());
    }

    @Test
    void constructor_shouldAcceptMissingOptionalParts_whenTheyAreNull() {
        final GitLabApiException exception = new GitLabApiException(500, "boom", null, HttpHeaders.empty(), null, null);

        assertSoftly(softly -> {
            softly.assertThat(exception.rawBody()).isEmpty();
            softly.assertThat(exception.method()).isEmpty();
            softly.assertThat(exception.uri()).isEmpty();
        });
    }

    @Test
    void subclasses_shouldKeepTheStatusAndTheMessage_whenCreated() {
        final List<GitLabApiException> exceptions = List.of(
                new GitLabUnauthorizedException(401, "a", null, HttpHeaders.empty(), HttpMethod.GET, URI_OF_CALL),
                new GitLabForbiddenException(403, "b", null, HttpHeaders.empty(), HttpMethod.GET, URI_OF_CALL),
                new GitLabNotFoundException(404, "c", null, HttpHeaders.empty(), HttpMethod.GET, URI_OF_CALL),
                new GitLabRateLimitException(429, "d", null, HttpHeaders.empty(), HttpMethod.GET, URI_OF_CALL),
                new GitLabValidationException(
                        400, "e", null, List.of(), HttpHeaders.empty(), HttpMethod.GET, URI_OF_CALL));

        assertThat(exceptions)
                .extracting(GitLabApiException::statusCode, Throwable::getMessage)
                .containsExactly(tuple(401, "a"), tuple(403, "b"), tuple(404, "c"), tuple(429, "d"), tuple(400, "e"));
    }

    @Test
    void errors_shouldBeACopy_whenTheSourceListChangesLater() {
        final List<GitLabValidationError> source = new ArrayList<>();
        source.add(new GitLabValidationError("a", List.of("x")));
        final GitLabValidationException exception =
                new GitLabValidationException(400, "m", null, source, HttpHeaders.empty(), null, null);

        source.add(new GitLabValidationError("b", List.of("y")));
        final Throwable thrown =
                catchThrowable(() -> exception.errors().add(new GitLabValidationError("c", List.of())));

        assertSoftly(softly -> {
            softly.assertThat(exception.errors()).hasSize(1);
            softly.assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
        });
    }

    @Test
    void validationError_shouldCopyTheMessages_whenTheSourceListChangesLater() {
        final List<String> messages = new ArrayList<>();
        messages.add("x");

        final GitLabValidationError error = new GitLabValidationError("field", messages);
        messages.add("y");

        assertThat(error.messages()).containsExactly("x");
    }

    @Test
    void validationError_shouldRefuse_whenFieldIsNull() {
        final Throwable thrown = catchThrowable(() -> new GitLabValidationError(null, List.of()));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void serialization_shouldKeepTheStatusAndTheErrors_whenAValidationExceptionIsRoundTripped() throws Exception {
        final GitLabValidationException original = new GitLabValidationException(
                400,
                "bio is too long",
                "raw",
                List.of(new GitLabValidationError("bio", List.of("is too long"))),
                HttpHeaders.of("X-Request-Id", "req-1"),
                HttpMethod.PUT,
                URI_OF_CALL);

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        final GitLabValidationException copy;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (GitLabValidationException) in.readObject();
        }

        assertSoftly(softly -> {
            softly.assertThat(copy.statusCode()).isEqualTo(400);
            softly.assertThat(copy).hasMessage("bio is too long");
            softly.assertThat(copy.errors()).containsExactly(new GitLabValidationError("bio", List.of("is too long")));
        });
    }
}
