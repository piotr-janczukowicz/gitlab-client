package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.http.HttpMethod.GET;
import static io.github.piotr_janczukowicz.test.http.StubHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.test.http.StubHttpServer;
import io.github.piotr_janczukowicz.testsupport.IntegrationTest;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@IntegrationTest
class GitLabClientsIntegrationTest {

    private static final String AGENT = "gitlab-test/1.0";

    @RegisterExtension
    static final StubHttpServer HTTP = new StubHttpServer();

    @Test
    void execute_shouldSendTheCredentialsAndHeadersTheClientIsConfiguredWith_whenCalled() throws IOException {
        HTTP.stub(GET, "/api/v4/projects/1", 200);

        final HttpResponse response;
        try (GitLabClient client = client(HTTP.baseUri())) {
            response = client.execute(HttpCall.get("/projects/1"));
        }

        assertThat(response.statusCode()).isEqualTo(200);
        HTTP.verifyHeader(GET, "/api/v4/projects/1", "Authorization", "Bearer token");
        HTTP.verifyHeader(GET, "/api/v4/projects/1", "User-Agent", AGENT);
        HTTP.verifyHeader(GET, "/api/v4/projects/1", "Accept", "application/json");
    }

    @Test
    void execute_shouldKeepEncodedSlashesInProjectPaths_whenProjectIsAddressedByItsPath() throws IOException {
        HTTP.stub(GET, "/api/v4/projects/group%2Fsub%2Fproject", 200);

        final HttpResponse response;
        try (GitLabClient client = client(HTTP.baseUri())) {
            response = client.execute(HttpCall.get("/projects/group%2Fsub%2Fproject"));
        }

        assertThat(response.statusCode()).isEqualTo(200);
        HTTP.verifyRequested(GET, "/api/v4/projects/group%2Fsub%2Fproject", 1);
    }

    @Test
    void execute_shouldKeepEncodedSlashesInFilePaths_whenFileIsInAFolder() throws IOException {
        HTTP.stub(GET, "/api/v4/projects/1/repository/files/src%2FREADME.md", 200);

        final HttpResponse response;
        try (GitLabClient client = client(HTTP.baseUri())) {
            response = client.execute(HttpCall.get("/projects/1/repository/files/src%2FREADME.md"));
        }

        assertThat(response.statusCode()).isEqualTo(200);
        HTTP.verifyRequested(GET, "/api/v4/projects/1/repository/files/src%2FREADME.md", 1);
    }

    @Test
    void execute_shouldSendTheRequestUnderTheSubPath_whenInstanceLivesUnderARelativeUrlRoot() throws IOException {
        HTTP.stub(GET, "/gitlab/api/v4/projects", 200);

        final HttpResponse response;
        try (GitLabClient client = client(HTTP.uri("/gitlab"))) {
            response = client.execute(HttpCall.get("/projects"));
        }

        assertThat(response.statusCode()).isEqualTo(200);
        HTTP.verifyRequested(GET, "/gitlab/api/v4/projects", 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v4", "/api/v4/", "/gitlab/api/v4"})
    void execute_shouldNotRepeatTheApiRoot_whenBaseUriAlreadyEndsInIt(final String root) throws IOException {
        final String expected = root.startsWith("/gitlab") ? "/gitlab/api/v4/projects" : "/api/v4/projects";
        HTTP.stub(GET, expected, 200);

        final HttpResponse response;
        try (GitLabClient client = client(HTTP.uri(root))) {
            response = client.execute(HttpCall.get("/projects"));
        }

        assertThat(response.statusCode()).isEqualTo(200);
        HTTP.verifyRequested(GET, expected, 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/../users", "/projects/../../users", "/%2e%2e/users"})
    void execute_shouldRefuse_whenThePathLeavesTheApiRoot(final String path) {
        HTTP.stub(GET, "/users", 200);
        final HttpCall call = HttpCall.get(path);

        final Throwable thrown;
        try (GitLabClient client = client(HTTP.baseUri())) {
            thrown = catchThrowable(() -> client.execute(call));
        }

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
        HTTP.verifyRequested(GET, "/users", 0);
    }

    @Test
    void execute_shouldSendTheSudoHeaderAsGiven_whenCallImpersonatesAUser() throws IOException {
        HTTP.stub(GET, "/api/v4/user", status(200));

        try (GitLabClient client = client(HTTP.baseUri())) {
            client.execute(HttpCall.get("/user").withHeader("Sudo", "alice"));
        }

        HTTP.verifyHeader(GET, "/api/v4/user", "Sudo", "alice");
    }

    private static GitLabClient client(final URI instanceRoot) {
        final GitLabClientConfiguration configuration =
                GitLabClientConfiguration.builder().baseUri(instanceRoot).build();
        return GitLabClients.create(AuthenticationProviders.bearer("token"), AGENT, configuration);
    }
}
