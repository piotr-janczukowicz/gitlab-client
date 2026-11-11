package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.http.HttpHeaderNames.CONTENT_TYPE;
import static io.github.piotr_janczukowicz.http.HttpMethod.GET;
import static io.github.piotr_janczukowicz.http.HttpMethod.POST;
import static io.github.piotr_janczukowicz.test.http.StubHttpServer.status;
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
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.http.transport.RetryConfiguration;
import io.github.piotr_janczukowicz.test.http.StubHttpServer;
import io.github.piotr_janczukowicz.testsupport.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@IntegrationTest
class GitLabErrorsIntegrationTest {

    @RegisterExtension
    static final StubHttpServer HTTP = new StubHttpServer();

    @Test
    void execute_shouldThrowUnauthorized_whenServerAnswersWithTheDocumentedBody() {
        HTTP.stub(GET, "/api/v4/user", json(401, "{\"message\":\"401 Unauthorized\"}"));

        final Throwable thrown = failure(HttpCall.get("/user"));

        assertThat(thrown).isInstanceOf(GitLabUnauthorizedException.class).hasMessage("401 Unauthorized");
    }

    @Test
    void execute_shouldThrowValidationWithTheFieldErrors_whenServerAnswersWith400AndAFieldMap() {
        HTTP.stub(
                POST,
                "/api/v4/users",
                json(400, "{\"message\":{\"bio\":[\"is too long (maximum is 255 characters)\"]}}"));

        final Throwable thrown = failure(HttpCall.post("/users"));

        assertThat(thrown)
                .isInstanceOfSatisfying(
                        GitLabValidationException.class,
                        validation -> assertThat(validation.errors())
                                .containsExactly(new GitLabValidationError(
                                        "bio", List.of("is too long (maximum is 255 characters)"))));
    }

    @Test
    void execute_shouldThrowAGenericException_whenServerAnswers400WithAStringMessage() {
        HTTP.stub(POST, "/api/v4/issues", json(400, "{\"message\":\"400 (Bad request) \\\"title\\\" not given\"}"));

        final Throwable thrown = failure(HttpCall.post("/issues"));

        assertSoftly(softly -> {
            softly.assertThat(thrown.getClass()).isEqualTo(GitLabApiException.class);
            softly.assertThat(thrown).hasMessage("400 (Bad request) \"title\" not given");
        });
    }

    @Test
    void execute_shouldThrowNotFoundAndForbidden_whenServerAnswers404And403() {
        HTTP.stub(GET, "/api/v4/projects/404", json(404, "{\"message\":\"404 Project Not Found\"}"));
        HTTP.stub(GET, "/api/v4/projects/403", json(403, "{\"message\":\"403 Forbidden\"}"));

        final Throwable notFound = failure(HttpCall.get("/projects/404"));
        final Throwable forbidden = failure(HttpCall.get("/projects/403"));

        assertSoftly(softly -> {
            softly.assertThat(notFound).isInstanceOf(GitLabNotFoundException.class);
            softly.assertThat(forbidden).isInstanceOf(GitLabForbiddenException.class);
        });
    }

    @Test
    void execute_shouldThrowRateLimitException_whenServerKeepsAnswering429() {
        HTTP.stub(GET, "/api/v4/limited", status(429).header("Retry-After", "0").body(bytes("Retry later")));

        final Throwable thrown = failure(HttpCall.get("/limited"));

        assertThat(thrown).isInstanceOf(GitLabRateLimitException.class);
    }

    @Test
    void execute_shouldKeepTheRawBodyOfAProxyPage_whenServerAnswers502WithHtml() {
        HTTP.stub(GET, "/api/v4/projects", status(502).body(bytes("<html>502 Bad Gateway</html>")));

        final Throwable thrown = failure(HttpCall.get("/projects"));

        assertThat(thrown)
                .isInstanceOfSatisfying(
                        GitLabApiException.class,
                        failure -> assertSoftly(softly -> {
                            softly.assertThat(failure.getClass()).isEqualTo(GitLabApiException.class);
                            softly.assertThat(failure).hasMessage("GitLab API request failed with HTTP 502");
                            softly.assertThat(failure.rawBody()).contains("<html>502 Bad Gateway</html>");
                        }));
    }

    private static Throwable failure(final HttpCall call) {
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(HTTP.baseUri())
                .retry(RetryConfiguration.builder()
                        .maxIoAttempts(1)
                        .maxRateLimitAttempts(1)
                        .maxServerErrorAttempts(1)
                        .build())
                .build();
        try (GitLabClient client =
                GitLabClients.create(AuthenticationProviders.bearer("token"), "gitlab-test/1.0", configuration)) {
            return catchThrowable(() -> client.execute(call));
        }
    }

    private static StubHttpServer.StubResponse json(final int status, final String body) {
        return status(status).header(CONTENT_TYPE, "application/json").body(bytes(body));
    }

    private static byte[] bytes(final String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
