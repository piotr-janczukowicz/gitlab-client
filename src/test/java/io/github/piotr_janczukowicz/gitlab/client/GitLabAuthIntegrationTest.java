package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.http.HttpMethod.GET;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProvider;
import io.github.piotr_janczukowicz.test.http.StubHttpServer;
import io.github.piotr_janczukowicz.testsupport.IntegrationTest;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@IntegrationTest
class GitLabAuthIntegrationTest {

    private static final String AGENT = "gitlab-test/1.0";
    private static final String PATH = "/api/v4/projects/1";

    @RegisterExtension
    static final StubHttpServer HTTP = new StubHttpServer();

    @Test
    void execute_shouldSendJobTokenAndNoAuthorization_whenTheClientUsesAJobToken() throws IOException {
        HTTP.stub(GET, PATH, 200);

        send(GitLabAuthenticationProviders.jobToken("job-secret"));

        HTTP.verifyHeader(GET, PATH, "JOB-TOKEN", "job-secret");
        HTTP.verifyNoHeader(GET, PATH, "Authorization");
    }

    @Test
    void execute_shouldSendBearerAndNoJobToken_whenTheClientUsesAPersonalToken() throws IOException {
        HTTP.stub(GET, PATH, 200);

        send(GitLabAuthenticationProviders.personalToken("pat-secret"));

        HTTP.verifyHeader(GET, PATH, "Authorization", "Bearer pat-secret");
        HTTP.verifyNoHeader(GET, PATH, "JOB-TOKEN");
    }

    @Test
    void execute_shouldDropTheJobTokenAndSudo_whenARedirectLeavesTheApiPathOnTheSameOrigin() throws IOException {
        HTTP.stubRedirect(PATH, HTTP.uri("/elsewhere").toString(), 302);
        HTTP.stub(GET, "/elsewhere", 200);

        try (GitLabClient client = GitLabClients.create(
                GitLabAuthenticationProviders.jobToken("job-secret"),
                AGENT,
                GitLabClientConfiguration.builder().baseUri(HTTP.baseUri()).build())) {
            client.execute(HttpCall.get("/projects/1").withHeaders(HttpHeaders.of("Sudo", "alice")));
        }

        HTTP.verifyHeader(GET, PATH, "JOB-TOKEN", "job-secret");
        HTTP.verifyHeader(GET, PATH, "Sudo", "alice");
        HTTP.verifyRequested(GET, "/elsewhere", 1);
        HTTP.verifyNoHeader(GET, "/elsewhere", "JOB-TOKEN");
        HTTP.verifyNoHeader(GET, "/elsewhere", "Sudo");
        HTTP.verifyNoHeader(GET, "/elsewhere", "Authorization");
    }

    private static void send(final AuthenticationProvider auth) throws IOException {
        try (GitLabClient client = GitLabClients.create(
                auth,
                AGENT,
                GitLabClientConfiguration.builder().baseUri(HTTP.baseUri()).build())) {
            client.execute(HttpCall.get("/projects/1"));
        }
    }
}
