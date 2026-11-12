package io.github.piotr_janczukowicz.gitlab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabApiException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabNotFoundException;
import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabErrorMapperTest {

    private static final URI URI_OF_CALL = URI.create("https://gitlab.example.test/api/v4/projects/1");

    private final GitLabErrorMapper mapper = GitLabErrorMapper.INSTANCE;

    @Test
    void map_shouldReturnTheExceptionOfTheStatus_whenResponseFails() {
        final HttpResponse response = new HttpResponse(
                404,
                HttpHeaders.empty(),
                HttpBody.of("{\"message\":\"404 Project Not Found\"}".getBytes(StandardCharsets.UTF_8)));

        final RuntimeException exception = mapper.map(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception).isInstanceOf(GitLabNotFoundException.class).hasMessage("404 Project Not Found");
    }

    @Test
    void redirectFailure_shouldReturnAGenericExceptionWithoutTheLocationHeader_whenRedirectIsRefused() {
        final HttpResponse response = new HttpResponse(
                302,
                HttpHeaders.of("Location", "https://elsewhere.example.test/x", "X-Request-Id", "req-1"),
                HttpBody.absent());

        final RuntimeException exception =
                mapper.redirectFailure(response, "redirect refused", "raw text", HttpMethod.GET, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabApiException.class,
                        failure -> assertSoftly(softly -> {
                            softly.assertThat(failure.getClass()).isEqualTo(GitLabApiException.class);
                            softly.assertThat(failure.statusCode()).isEqualTo(302);
                            softly.assertThat(failure).hasMessage("redirect refused");
                            softly.assertThat(failure.rawBody()).contains("raw text");
                            softly.assertThat(failure.headers().get("Location")).isEmpty();
                            softly.assertThat(failure.headers().get("X-Request-Id"))
                                    .contains("req-1");
                            softly.assertThat(failure.method()).contains(HttpMethod.GET);
                            softly.assertThat(failure.uri()).contains(URI_OF_CALL);
                        }));
    }
}
