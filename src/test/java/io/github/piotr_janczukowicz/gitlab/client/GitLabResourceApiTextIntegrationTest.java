package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.http.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.http.HttpHeaderNames;
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.test.http.StubHttpServer;
import io.github.piotr_janczukowicz.testsupport.IntegrationTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@IntegrationTest
class GitLabResourceApiTextIntegrationTest {

    @RegisterExtension
    static final StubHttpServer HTTP = new StubHttpServer();

    static final class TextApi extends GitLabResourceApi {

        TextApi(final GitLabCaller caller) {
            super(caller);
        }

        String text(final HttpCall call) throws IOException {
            return fetchText(call);
        }
    }

    @Test
    void fetchText_shouldReturnTheBodyAsText_whenTheServerAnswersPlainText() throws IOException {
        HTTP.stub(
                GET,
                "/api/v4/script.sh",
                StubHttpServer.status(200)
                        .header(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
                        .body("echo café".getBytes(StandardCharsets.UTF_8)));

        final String text;
        try (GitLabClient client = client()) {
            text = new TextApi(GitLabCaller.of(client)).text(HttpCall.get("/script.sh"));
        }

        assertThat(text).isEqualTo("echo café");
    }

    @Test
    void fetchText_shouldRejectNull_whenTheCallIsMissing() throws IOException {
        try (GitLabClient client = client()) {
            final TextApi api = new TextApi(GitLabCaller.of(client));

            final Throwable thrown = catchThrowable(() -> api.text(null));

            assertThat(thrown).isInstanceOf(NullPointerException.class);
        }
    }

    private static GitLabClient client() {
        final GitLabClientConfiguration configuration =
                GitLabClientConfiguration.builder().baseUri(HTTP.baseUri()).build();
        return GitLabClients.create(AuthenticationProviders.bearer("token"), "gitlab-test/1.0", configuration);
    }
}
