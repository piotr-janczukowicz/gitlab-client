package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.http.HttpHeaderNames.CONTENT_TYPE;
import static io.github.piotr_janczukowicz.http.HttpMethod.GET;
import static io.github.piotr_janczukowicz.test.http.StubHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.test.http.StubHttpServer;
import io.github.piotr_janczukowicz.testsupport.IntegrationTest;
import io.github.piotr_janczukowicz.uri.PathTemplate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
class GitLabCallerIntegrationTest {

    private static final PathTemplate PROJECT = PathTemplate.of("/projects/{id}");
    private static final String BODY =
            "{\"id\":7,\"path_with_namespace\":\"group/sub/project\",\"created_at\":\"2024-02-29T23:59:59.123Z\"}";

    @RegisterExtension
    static final StubHttpServer HTTP = new StubHttpServer();

    record Project(long id, String pathWithNamespace, Instant createdAt) {}

    static final class ProjectsApi extends GitLabResourceApi {

        ProjectsApi(final GitLabCaller caller) {
            super(caller);
        }

        Project project(final String id) throws IOException {
            return caller.fetch(get(PROJECT, id), Project.class);
        }
    }

    @Test
    void fetch_shouldDecodeSnakeCaseJsonIntoARecord_whenServerReturnsAProject() throws IOException {
        HTTP.stub(GET, "/api/v4/projects/7", json(BODY));

        final Project project;
        try (GitLabClient client = client()) {
            project = GitLabCaller.of(client).fetch(HttpCall.get("/projects/7"), Project.class);
        }

        assertSoftly(softly -> {
            softly.assertThat(project.id()).isEqualTo(7L);
            softly.assertThat(project.pathWithNamespace()).isEqualTo("group/sub/project");
            softly.assertThat(project.createdAt()).isEqualTo(Instant.parse("2024-02-29T23:59:59.123Z"));
        });
    }

    @ParameterizedTest
    @CsvSource({
        "group/sub/project,group%2Fsub%2Fproject",
        "src/README.md,src%2FREADME.md",
        "a b,a%20b",
        "a+b,a+b",
        "100%,100%25",
        "café,caf%C3%A9",
        "42,42"
    })
    void get_shouldEncodeEachValueAsOneSegment_whenTheValueHoldsSpecialCharacters(
            final String value, final String encoded) throws IOException {
        HTTP.stub(GET, "/api/v4/projects/" + encoded, json(BODY));

        final Project project;
        try (GitLabClient client = client()) {
            project = new ProjectsApi(GitLabCaller.of(client)).project(value);
        }

        assertThat(project.id()).isEqualTo(7L);
        HTTP.verifyRequested(GET, "/api/v4/projects/" + encoded, 1);
    }

    @Test
    void of_shouldRefuse_whenClientIsNull() {
        final Throwable thrown = catchThrowable(() -> GitLabCaller.of(null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_shouldRefuse_whenCallerIsNull() {
        final Throwable thrown = catchThrowable(() -> new ProjectsApi(null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    private static GitLabClient client() {
        final GitLabClientConfiguration configuration =
                GitLabClientConfiguration.builder().baseUri(HTTP.baseUri()).build();
        return GitLabClients.create(AuthenticationProviders.bearer("token"), "gitlab-test/1.0", configuration);
    }

    private static StubHttpServer.StubResponse json(final String body) {
        return status(200).header(CONTENT_TYPE, "application/json").body(body.getBytes(StandardCharsets.UTF_8));
    }
}
