package io.github.piotr_janczukowicz.gitlab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.gitlab.client.pagination.GitLabListSpec;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.json.JsonTypes;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabCallerListTest {

    private static final GitLabListSpec<String> SPEC = GitLabListSpec.offset(JsonTypes.listOf(String.class));

    @Test
    void list_shouldRefuseACallThatIsNotAGet_whenTheCallPosts() {
        final Throwable thrown;
        try (GitLabClient client = client()) {
            final GitLabCaller caller = GitLabCaller.of(client);
            final HttpCall call = HttpCall.post("/projects");
            thrown = catchThrowable(() -> caller.list(call, GitLabPageRequest.byPage(), SPEC));
        }

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must be a GET");
    }

    @Test
    void list_shouldRefuseACallWithABody_whenTheCallIsAGet() {
        final Throwable thrown;
        try (GitLabClient client = client()) {
            final GitLabCaller caller = GitLabCaller.of(client);
            final HttpCall call =
                    HttpCall.get("/projects").withBody(HttpBody.of("{}".getBytes(StandardCharsets.UTF_8)));
            thrown = catchThrowable(() -> caller.list(call, GitLabPageRequest.byPage(), SPEC));
        }

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not have a body");
    }

    @Test
    void list_shouldRefuse_whenAnArgumentIsNull() {
        final Throwable noRequest;
        final Throwable noSpec;
        try (GitLabClient client = client()) {
            final GitLabCaller caller = GitLabCaller.of(client);
            final HttpCall call = HttpCall.get("/projects");
            noRequest = catchThrowable(() -> caller.list(call, null, SPEC));
            noSpec = catchThrowable(() -> caller.list(call, GitLabPageRequest.byPage(), null));
        }

        assertThat(noRequest).isInstanceOf(NullPointerException.class);
        assertThat(noSpec).isInstanceOf(NullPointerException.class);
    }

    private static GitLabClient client() {
        return GitLabClients.create(AuthenticationProviders.bearer("token"), "gitlab-test/1.0");
    }
}
