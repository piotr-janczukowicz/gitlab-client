package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.http.HttpHeaderNames.CONTENT_TYPE;
import static io.github.piotr_janczukowicz.http.HttpMethod.GET;
import static io.github.piotr_janczukowicz.test.http.StubHttpServer.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.pagination.GitLabListSpec;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabKeysetRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabOffsetRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPage;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.json.JsonTypes;
import io.github.piotr_janczukowicz.test.http.StubHttpServer;
import io.github.piotr_janczukowicz.testsupport.IntegrationTest;
import io.github.piotr_janczukowicz.uri.PathTemplate;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@IntegrationTest
class GitLabPaginationIntegrationTest {

    private static final PathTemplate ISSUES = PathTemplate.of("/projects/{id}/issues");
    private static final String ISSUES_PATH = "/api/v4/projects/group%2Fsub%2Fproject/issues";

    @RegisterExtension
    static final StubHttpServer HTTP = new StubHttpServer();

    record Issue(long id, String title) {}

    static final class IssuesApi extends GitLabResourceApi {

        IssuesApi(final GitLabCaller caller) {
            super(caller);
        }

        GitLabPage<Issue> issues(final String project, final GitLabPageRequest request) throws IOException {
            return pageList(ISSUES, Map.of("state", "opened"), request, JsonTypes.listOf(Issue.class), project);
        }
    }

    @Test
    void pageList_shouldReadEveryPageInOrderThroughRealRequests_whenEachPageNamesTheNextOne() throws IOException {
        final String base = HTTP.baseUri().toString().replaceAll("/$", "");
        HTTP.stub(
                GET,
                ISSUES_PATH,
                json("[{\"id\":1,\"title\":\"a\"},{\"id\":2,\"title\":\"b\"}]")
                        .header("Link", "<" + base + ISSUES_PATH + "?state=opened&page=2&per_page=2>; rel=\"next\"")
                        .header("X-Total", "5"),
                json("[{\"id\":3,\"title\":\"c\"},{\"id\":4,\"title\":\"d\"}]")
                        .header("Link", "<" + base + ISSUES_PATH + "?state=opened&page=3&per_page=2>; rel=\"next\""),
                json("[{\"id\":5,\"title\":\"e\"}]"));

        final List<Issue> issues;
        final Integer total;
        try (GitLabClient client = client()) {
            final GitLabPage<Issue> first =
                    new IssuesApi(GitLabCaller.of(client)).issues("group/sub/project", GitLabOffsetRequest.of(1, 2));
            total = first.totalCount();
            issues = first.all();
        }

        assertSoftly(softly -> {
            softly.assertThat(issues).extracting(Issue::id).containsExactly(1L, 2L, 3L, 4L, 5L);
            softly.assertThat(total).isEqualTo(5);
        });
        HTTP.verifyRequested(GET, ISSUES_PATH, 3);
        HTTP.verifyQueryParam(GET, ISSUES_PATH, "state", "opened");
        HTTP.verifyQueryParam(GET, ISSUES_PATH, "per_page", "2");
    }

    @Test
    void pageList_shouldWalkByXNextPage_whenTheServerSendsNoLinkHeader() throws IOException {
        HTTP.stub(
                GET,
                ISSUES_PATH,
                json("[{\"id\":1,\"title\":\"a\"}]").header("X-Next-Page", "2"),
                json("[{\"id\":2,\"title\":\"b\"}]").header("X-Next-Page", ""));

        final List<Issue> issues;
        try (GitLabClient client = client()) {
            issues = new IssuesApi(GitLabCaller.of(client))
                    .issues("group/sub/project", GitLabPageRequest.byPage())
                    .all();
        }

        assertThat(issues).extracting(Issue::id).containsExactly(1L, 2L);
        HTTP.verifyRequested(GET, ISSUES_PATH, 2);
    }

    @Test
    void list_shouldSendTheKeysetCursorAsTheServerEncodedIt_whenTheLinkCarriesEncodedCharacters() throws IOException {
        final String base = HTTP.baseUri().toString().replaceAll("/$", "");
        final String path = "/api/v4/projects/1/issues";
        HTTP.stub(
                GET,
                path,
                json("[{\"id\":1,\"title\":\"a\"}]")
                        .header(
                                "Link",
                                "<" + base + path
                                        + "?pagination=keyset&order_by=id&sort=asc&per_page=1&cursor=ab%2Bc%3D%3D%2Fd%7Ce>;"
                                        + " rel=\"next\""),
                json("[{\"id\":2,\"title\":\"b\"}]"));

        final List<Issue> issues;
        try (GitLabClient client = client()) {
            issues = GitLabCaller.of(client)
                    .list(
                            HttpCall.get("/projects/1/issues"),
                            GitLabKeysetRequest.of("id", "asc", 1),
                            GitLabListSpec.offsetOrKeyset(JsonTypes.listOf(Issue.class)))
                    .all();
        }

        assertThat(issues).extracting(Issue::id).containsExactly(1L, 2L);
        HTTP.verifyQueryParam(GET, path, "cursor", "ab+c==/d|e");
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
