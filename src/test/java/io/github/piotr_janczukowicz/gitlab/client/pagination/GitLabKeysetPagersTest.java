package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.GitLabClientConfiguration;
import io.github.piotr_janczukowicz.gitlab.client.internal.ClientStacks;
import io.github.piotr_janczukowicz.gitlab.client.internal.DefaultGitLabClient;
import io.github.piotr_janczukowicz.gitlab.json.GitLabJsonCodecs;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabKeysetRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPage;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequestKind;
import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.json.JsonCodec;
import io.github.piotr_janczukowicz.json.JsonTypes;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import io.github.piotr_janczukowicz.test.http.ScriptedTransport;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabKeysetPagersTest {

    private static final URI BASE = URI.create("https://gitlab.example.test");
    private static final String API = "https://gitlab.example.test/api/v4";
    private static final JsonCodec JSON = GitLabJsonCodecs.defaults();
    private static final GitLabListSpec<Item> KEYSET = GitLabListSpec.offsetOrKeyset(JsonTypes.listOf(Item.class));

    record Item(long id) {}

    @Test
    void start_shouldSendTheFourKeysetParameters_whenAKeysetRequestIsGiven() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[{\"id\":1}]"));

        try (DefaultGitLabClient client = client(scripted)) {
            start(client, Map.of("owned", List.of("true")), GitLabKeysetRequest.of("id", "asc", 50));
        }

        assertThat(scripted.request(0).uri().getRawQuery())
                .isEqualTo("owned=true&pagination=keyset&order_by=id&sort=asc&per_page=50");
    }

    @Test
    void start_shouldSendTheConfiguredPageSize_whenTheRequestNamesNone() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[]"));

        try (DefaultGitLabClient client = client(scripted)) {
            start(client, Map.of(), GitLabKeysetRequest.of("id", "desc"));
        }

        assertThat(scripted.request(0).uri().getRawQuery())
                .isEqualTo("pagination=keyset&order_by=id&sort=desc&per_page="
                        + PaginationConfiguration.defaults().perPage());
    }

    @Test
    void next_shouldFollowTheLinkVerbatim_whenItCarriesACursorTheClientDoesNotKnow() throws IOException {
        final String cursor = "eyJuYW1l%2BIjoiRm%3D%3Dx%2Fy%7Cz";
        final String link = API + "/groups?pagination=keyset&per_page=2&order_by=name&sort=asc&cursor=" + cursor;
        final ScriptedTransport scripted =
                ScriptedTransport.replying(page("[{\"id\":1},{\"id\":2}]", "Link", next(link)), page("[{\"id\":3}]"));

        final List<Item> items;
        try (DefaultGitLabClient client = client(scripted)) {
            items = start(client, "/groups", Map.of(), GitLabKeysetRequest.of("name", "asc", 2))
                    .all();
        }

        assertSoftly(softly -> {
            softly.assertThat(items).extracting(Item::id).containsExactly(1L, 2L, 3L);
            softly.assertThat(scripted.request(1).uri().toString()).isEqualTo(link);
            softly.assertThat(scripted.request(1).uri().getRawQuery()).endsWith("cursor=" + cursor);
            softly.assertThat(scripted.calls()).isEqualTo(2);
        });
    }

    @Test
    void next_shouldEndTheWalk_whenThereIsNoLinkHeader() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[{\"id\":1}]"));

        final Optional<GitLabPage<Item>> next;
        try (DefaultGitLabClient client = client(scripted)) {
            next = start(client, Map.of(), GitLabKeysetRequest.of("id", "asc")).next();
        }

        assertSoftly(softly -> {
            softly.assertThat(next).isEmpty();
            softly.assertThat(scripted.calls()).isEqualTo(1);
        });
    }

    @Test
    void next_shouldNotBuildAUrlFromXNextPage_whenAKeysetResponseHasNoLink() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[{\"id\":1}]", "X-Next-Page", "2"));

        final Optional<GitLabPage<Item>> next;
        try (DefaultGitLabClient client = client(scripted)) {
            next = start(client, Map.of(), GitLabKeysetRequest.of("id", "asc")).next();
        }

        assertSoftly(softly -> {
            softly.assertThat(next).isEmpty();
            softly.assertThat(scripted.calls()).isEqualTo(1);
        });
    }

    @Test
    void next_shouldEndTheWalk_whenTheLinkHeaderHasNoNext() throws IOException {
        final ScriptedTransport scripted =
                ScriptedTransport.replying(page("[{\"id\":1}]", "Link", "<" + API + "/projects>; rel=\"first\""));

        final Optional<GitLabPage<Item>> next;
        try (DefaultGitLabClient client = client(scripted)) {
            next = start(client, Map.of(), GitLabKeysetRequest.of("id", "asc")).next();
        }

        assertThat(next).isEmpty();
    }

    @Test
    void totalCount_shouldBeNull_whenAKeysetResponseHasNoXTotal() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[{\"id\":1}]"));

        final GitLabPage<Item> first;
        try (DefaultGitLabClient client = client(scripted)) {
            first = start(client, Map.of(), GitLabKeysetRequest.of("id", "asc"));
        }

        assertThat(first.totalCount()).isNull();
    }

    @Test
    void next_shouldSendTheFirstCallHeadersOnEveryPage_whenAKeysetListIsReadAsAnotherUser() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next(API + "/projects?pagination=keyset&id_after=1")), page("[]"));

        try (DefaultGitLabClient client = client(scripted)) {
            start(
                            client,
                            new GitLabPageTarget("/projects", Map.of(), HttpHeaders.of("Sudo", "42")),
                            GitLabKeysetRequest.of("id", "asc"))
                    .all();
        }

        assertThat(scripted.request(1).headers().get("Sudo")).contains("42");
    }

    @Test
    void start_shouldRefuseBeforeSending_whenTheListIsOffsetOnly() {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            thrown = catchThrowable(() -> GitLabPagers.start(
                    client,
                    JSON,
                    client.pagination(),
                    new GitLabPageTarget("/projects", Map.of(), HttpHeaders.empty()),
                    GitLabKeysetRequest.of("id", "asc"),
                    GitLabListSpec.offset(JsonTypes.listOf(Item.class))));
        }

        assertSoftly(softly -> {
            softly.assertThat(thrown)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("KEYSET");
            softly.assertThat(scripted.calls()).isZero();
        });
    }

    @Test
    void start_shouldRefuseAPageSizeAbove100_whenAKeysetRequestNamesOne() {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            thrown = catchThrowable(() -> start(client, Map.of(), GitLabKeysetRequest.of("id", "asc", 101)));
        }

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("perPage");
    }

    @Test
    void offsetOrKeyset_shouldAllowNoneOffsetAndKeysetRequests_whenCreated() {
        assertThat(KEYSET.allowedKinds())
                .containsExactlyInAnyOrder(
                        GitLabPageRequestKind.NONE, GitLabPageRequestKind.OFFSET, GitLabPageRequestKind.KEYSET);
    }

    @Test
    void next_shouldStillFallBackToXNextPage_whenAnOffsetRequestIsMadeOnAKeysetCapableList() throws IOException {
        final ScriptedTransport scripted =
                ScriptedTransport.replying(page("[{\"id\":1}]", "X-Next-Page", "2"), page("[{\"id\":2}]"));

        final List<Item> items;
        try (DefaultGitLabClient client = client(scripted)) {
            items = start(client, Map.of(), GitLabPageRequest.byPage()).all();
        }

        assertThat(items).extracting(Item::id).containsExactly(1L, 2L);
    }

    private static GitLabPage<Item> start(
            final DefaultGitLabClient client, final Map<String, List<String>> query, final GitLabPageRequest request)
            throws IOException {
        return start(client, "/projects", query, request);
    }

    private static GitLabPage<Item> start(
            final DefaultGitLabClient client,
            final String path,
            final Map<String, List<String>> query,
            final GitLabPageRequest request)
            throws IOException {
        return start(client, new GitLabPageTarget(path, query, HttpHeaders.empty()), request);
    }

    private static GitLabPage<Item> start(
            final DefaultGitLabClient client, final GitLabPageTarget target, final GitLabPageRequest request)
            throws IOException {
        return GitLabPagers.start(client, JSON, client.pagination(), target, request, KEYSET);
    }

    private static DefaultGitLabClient client(final ScriptedTransport scripted) {
        return ClientStacks.assemble(
                        scripted.transport(),
                        AuthenticationProviders.bearer("token"),
                        "gitlab-test/1.0",
                        GitLabClientConfiguration.builder()
                                .baseUri(BASE)
                                .pagination(PaginationConfiguration.defaults())
                                .build(),
                        (transport, retry) -> transport)
                .build();
    }

    private static HttpResponse page(final String json, final String... headers) {
        return new HttpResponse(200, HttpHeaders.of(headers), HttpBody.of(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String next(final String uri) {
        return "<" + uri + ">; rel=\"next\"";
    }
}
