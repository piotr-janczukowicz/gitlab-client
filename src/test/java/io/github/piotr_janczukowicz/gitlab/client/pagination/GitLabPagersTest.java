package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.GitLabClientConfiguration;
import io.github.piotr_janczukowicz.gitlab.client.internal.ClientStacks;
import io.github.piotr_janczukowicz.gitlab.client.internal.DefaultGitLabClient;
import io.github.piotr_janczukowicz.gitlab.json.GitLabJsonCodecs;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPage;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.json.JsonCodec;
import io.github.piotr_janczukowicz.json.JsonDecodeException;
import io.github.piotr_janczukowicz.json.JsonTypes;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import io.github.piotr_janczukowicz.pagination.PaginationException;
import io.github.piotr_janczukowicz.pagination.PaginationLimitExceededException;
import io.github.piotr_janczukowicz.pagination.PaginationLoopException;
import io.github.piotr_janczukowicz.test.http.ScriptedTransport;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabPagersTest {

    private static final URI BASE = URI.create("https://gitlab.example.test");
    private static final String API = "https://gitlab.example.test/api/v4";
    private static final JsonCodec JSON = GitLabJsonCodecs.defaults();
    private static final GitLabListSpec<Item> SPEC = GitLabListSpec.offset(JsonTypes.listOf(Item.class));

    record Item(long id) {}

    @Test
    void start_shouldRequestPageOneWithThePageSize_whenAnOffsetRequestIsGiven() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[{\"id\":1}]"));

        final GitLabPage<Item> first;
        try (DefaultGitLabClient client =
                client(scripted, PaginationConfiguration.builder().perPage(50).build())) {
            first = start(client, "/projects/1/issues", Map.of("state", List.of("opened")), GitLabPageRequest.byPage());
        }

        assertSoftly(softly -> {
            softly.assertThat(scripted.request(0).uri().getRawPath()).isEqualTo("/api/v4/projects/1/issues");
            softly.assertThat(scripted.request(0).uri().getRawQuery()).isEqualTo("state=opened&page=1&per_page=50");
            softly.assertThat(first.items()).containsExactly(new Item(1));
            softly.assertThat(first.pageCount()).isEqualTo(1);
        });
    }

    @Test
    void start_shouldSendOnlyTheBaseQuery_whenTheRequestAddsNoParameters() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[]"));

        try (DefaultGitLabClient client = client(scripted)) {
            start(client, "/projects", Map.of("owned", List.of("true")), GitLabPageRequest.none());
        }

        assertThat(scripted.request(0).uri().getRawQuery()).isEqualTo("owned=true");
    }

    @Test
    void pages_shouldFollowTheLinkHeaderThroughEveryPage_whenEachPageNamesTheNextOne() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1},{\"id\":2}]", "Link", next(API + "/projects?page=2&per_page=2")),
                page("[{\"id\":3},{\"id\":4}]", "Link", next(API + "/projects?page=3&per_page=2")),
                page("[{\"id\":5}]", "Link", "<" + API + "/projects?page=1&per_page=2>; rel=\"first\""));

        final List<GitLabPage<Item>> pages;
        try (DefaultGitLabClient client = client(scripted)) {
            pages = start(client, "/projects", Map.of(), GitLabPageRequest.byPage())
                    .pages()
                    .toList();
        }

        assertSoftly(softly -> {
            softly.assertThat(pages).extracting(GitLabPage::pageCount).containsExactly(1, 2, 3);
            softly.assertThat(pages.stream().flatMap(page -> page.items().stream()))
                    .extracting(Item::id)
                    .containsExactly(1L, 2L, 3L, 4L, 5L);
            softly.assertThat(scripted.calls()).isEqualTo(3);
        });
    }

    @Test
    void next_shouldEndTheWalk_whenTheLinkHeaderHasNoNextEvenIfXNextPageIsSet() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", "<" + API + "/projects?page=1>; rel=\"first\"", "X-Next-Page", "3"));

        final Optional<GitLabPage<Item>> next;
        try (DefaultGitLabClient client = client(scripted)) {
            next = start(client, "/projects", Map.of(), GitLabPageRequest.byPage())
                    .next();
        }

        assertSoftly(softly -> {
            softly.assertThat(next).isEmpty();
            softly.assertThat(scripted.calls()).isEqualTo(1);
        });
    }

    @Test
    void next_shouldWarn_whenTheLinkHeaderHasNoNextButXNextPageNamesAPage() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", "<" + API + "/projects?page=1>; rel=\"first\"", "X-Next-Page", "3"));

        try (LogCaptor logs = LogCaptor.forClass(GitLabPagers.class);
                DefaultGitLabClient client = client(scripted)) {
            start(client, "/projects", Map.of(), GitLabPageRequest.byPage()).next();

            assertThat(logs.getWarnLogs())
                    .singleElement()
                    .asString()
                    .contains("X-Next-Page")
                    .contains("rel=\"next\"")
                    .doesNotContain(API);
        }
    }

    @Test
    void next_shouldNotWarn_whenTheLinkHeaderHasNoNextAndXNextPageIsEmpty() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", "<" + API + "/projects?page=1>; rel=\"first\"", "X-Next-Page", ""));

        try (LogCaptor logs = LogCaptor.forClass(GitLabPagers.class);
                DefaultGitLabClient client = client(scripted)) {
            start(client, "/projects", Map.of(), GitLabPageRequest.byPage()).next();

            assertThat(logs.getWarnLogs()).isEmpty();
        }
    }

    @Test
    void next_shouldRequestTheNextPageNumber_whenThereIsNoLinkHeaderButXNextPageIs() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "X-Next-Page", "2"), page("[{\"id\":2}]", "X-Next-Page", ""));

        final List<Item> items;
        try (DefaultGitLabClient client =
                client(scripted, PaginationConfiguration.builder().perPage(20).build())) {
            items = start(client, "/projects/1/issues", Map.of("state", List.of("opened")), GitLabPageRequest.byPage())
                    .all();
        }

        assertSoftly(softly -> {
            softly.assertThat(items).extracting(Item::id).containsExactly(1L, 2L);
            softly.assertThat(scripted.request(1).uri().getRawPath()).isEqualTo("/api/v4/projects/1/issues");
            softly.assertThat(scripted.request(1).uri().getRawQuery().split("&"))
                    .containsExactlyInAnyOrder("state=opened", "page=2", "per_page=20");
            softly.assertThat(scripted.calls()).isEqualTo(2);
        });
    }

    @Test
    void next_shouldAddThePageParameter_whenTheFirstRequestHadNone() throws IOException {
        final ScriptedTransport scripted =
                ScriptedTransport.replying(page("[{\"id\":1}]", "X-Next-Page", "2"), page("[]"));

        try (DefaultGitLabClient client = client(scripted)) {
            start(client, "/projects", Map.of("owned", List.of("true")), GitLabPageRequest.none())
                    .next();
        }

        assertThat(scripted.request(1).uri().getRawQuery()).isEqualTo("owned=true&page=2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "0", "-1", "abc", "1.5", "99999999999999999999"})
    void next_shouldEndTheWalk_whenXNextPageIsNotAPositiveWholeNumber(final String value) throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[{\"id\":1}]", "X-Next-Page", value));

        final Optional<GitLabPage<Item>> next;
        try (DefaultGitLabClient client = client(scripted)) {
            next = start(client, "/projects", Map.of(), GitLabPageRequest.byPage())
                    .next();
        }

        assertSoftly(softly -> {
            softly.assertThat(next).isEmpty();
            softly.assertThat(scripted.calls()).isEqualTo(1);
        });
    }

    @Test
    void next_shouldEndTheWalk_whenNeitherPaginationHeaderIsPresent() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[{\"id\":1}]"));

        final Optional<GitLabPage<Item>> next;
        try (DefaultGitLabClient client = client(scripted)) {
            next = start(client, "/projects", Map.of(), GitLabPageRequest.byPage())
                    .next();
        }

        assertThat(next).isEmpty();
    }

    @Test
    void next_shouldRequestTheSameEncodedPath_whenThePageComesFromTheFallback() throws IOException {
        final ScriptedTransport scripted =
                ScriptedTransport.replying(page("[{\"id\":1}]", "X-Next-Page", "2"), page("[]"));

        try (DefaultGitLabClient client = client(scripted)) {
            start(client, "/projects/group%2Fsub%2Fproject/issues", Map.of(), GitLabPageRequest.byPage())
                    .next();
        }

        assertSoftly(softly -> {
            softly.assertThat(scripted.request(0).uri().getRawPath())
                    .isEqualTo("/api/v4/projects/group%2Fsub%2Fproject/issues");
            softly.assertThat(scripted.request(1).uri().getRawPath())
                    .isEqualTo("/api/v4/projects/group%2Fsub%2Fproject/issues");
        });
    }

    @Test
    void next_shouldResolveARelativeLinkAndKeepTheEncodedPath_whenTheLinkIsNotAbsolute() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next("/api/v4/projects/group%2Fsub%2Fproject/issues?page=2&per_page=100")),
                page("[]"));

        try (DefaultGitLabClient client = client(scripted)) {
            start(client, "/projects/group%2Fsub%2Fproject/issues", Map.of(), GitLabPageRequest.byPage())
                    .next();
        }

        assertSoftly(softly -> {
            softly.assertThat(scripted.request(1).uri().getRawPath())
                    .isEqualTo("/api/v4/projects/group%2Fsub%2Fproject/issues");
            softly.assertThat(scripted.request(1).uri().getRawQuery()).isEqualTo("page=2&per_page=100");
        });
    }

    @Test
    void next_shouldSendTheHeadersOfTheFirstCallOnEveryPage_whenTheCallImpersonatesAUser() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next(API + "/projects?page=2")),
                page("[{\"id\":2}]", "Link", next(API + "/projects?page=3")),
                page("[{\"id\":3}]"));

        try (DefaultGitLabClient client = client(scripted)) {
            start(
                            client,
                            new GitLabPageTarget("/projects", Map.of(), HttpHeaders.of("Sudo", "alice")),
                            GitLabPageRequest.byPage())
                    .all();
        }

        assertSoftly(softly -> {
            softly.assertThat(scripted.request(0).headers().get("Sudo")).contains("alice");
            softly.assertThat(scripted.request(1).headers().get("Sudo")).contains("alice");
            softly.assertThat(scripted.request(2).headers().get("Sudo")).contains("alice");
        });
    }

    @Test
    void next_shouldFollowAnAbsoluteLinkUnderTheSubPath_whenTheInstanceRunsUnderARelativeUrlRoot() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next("https://host.example.test/gitlab/api/v4/projects?page=2")),
                page("[{\"id\":2}]"));
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(URI.create("https://host.example.test/gitlab"))
                .build();

        final List<Item> items;
        try (DefaultGitLabClient client = client(scripted, configuration)) {
            items = start(client, "/projects", Map.of(), GitLabPageRequest.byPage())
                    .all();
        }

        assertSoftly(softly -> {
            softly.assertThat(items).extracting(Item::id).containsExactly(1L, 2L);
            softly.assertThat(scripted.request(1).uri().getRawPath()).isEqualTo("/gitlab/api/v4/projects");
        });
    }

    @Test
    void next_shouldRefuseALinkWithAnotherScheme_whenTheHostIsTheSameAndExplainWhy() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next("http://gitlab.example.test/api/v4/projects?page=2")), page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            final GitLabPage<Item> first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
            thrown = catchThrowable(first::next);
        }

        assertSoftly(softly -> {
            softly.assertThat(thrown)
                    .isInstanceOf(PaginationException.class)
                    .hasMessageContaining("outside the API base URI")
                    .hasMessageContaining("external_url");
            softly.assertThat(scripted.calls()).isEqualTo(1);
        });
    }

    @Test
    void next_shouldRefuseALinkToAnotherHostWithoutQuotingItsQuery_whenTheLinkLeavesTheInstance() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next("https://evil.example.test/api/v4/projects?token=secret&page=2")),
                page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            final GitLabPage<Item> first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
            thrown = catchThrowable(first::next);
        }

        assertSoftly(softly -> {
            softly.assertThat(thrown).isInstanceOf(PaginationException.class).hasMessageNotContaining("secret");
            softly.assertThat(scripted.calls()).isEqualTo(1);
        });
    }

    @Test
    void next_shouldThrowALoopException_whenALinkPointsBackAtTheFirstRequest() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next(API + "/projects?page=1&per_page=100")), page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            final GitLabPage<Item> first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
            thrown = catchThrowable(first::next);
        }

        assertThat(thrown).isInstanceOf(PaginationLoopException.class);
    }

    @Test
    void next_shouldThrowALoopException_whenTwoPagesNameEachOther() {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next(API + "/projects?page=2")),
                page("[{\"id\":2}]", "Link", next(API + "/projects?page=2")),
                page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            thrown = catchThrowable(() -> start(client, "/projects", Map.of(), GitLabPageRequest.byPage())
                    .all());
        }

        assertThat(thrown).isInstanceOf(PaginationLoopException.class);
    }

    @Test
    void next_shouldThrowALimitException_whenTheMaxPagesWereRead() {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next(API + "/projects?page=2")),
                page("[{\"id\":2}]", "Link", next(API + "/projects?page=3")),
                page("[{\"id\":3}]"));

        final Throwable thrown;
        try (DefaultGitLabClient client =
                client(scripted, PaginationConfiguration.builder().maxPages(2).build())) {
            thrown = catchThrowable(() -> start(client, "/projects", Map.of(), GitLabPageRequest.byPage())
                    .all());
        }

        assertSoftly(softly -> {
            softly.assertThat(thrown).isInstanceOf(PaginationLimitExceededException.class);
            softly.assertThat(scripted.calls()).isEqualTo(2);
        });
    }

    @Test
    void stream_shouldNotRequestAPageItDoesNotNeed_whenTheCallerStopsEarly() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1},{\"id\":2},{\"id\":3}]", "Link", next(API + "/projects?page=2")),
                page("[{\"id\":4},{\"id\":5},{\"id\":6}]", "Link", next(API + "/projects?page=3")),
                page("[{\"id\":7}]"));

        final List<Item> items;
        try (DefaultGitLabClient client = client(scripted)) {
            items = start(client, "/projects", Map.of(), GitLabPageRequest.byPage()).stream()
                    .limit(5)
                    .toList();
        }

        assertSoftly(softly -> {
            softly.assertThat(items).extracting(Item::id).containsExactly(1L, 2L, 3L, 4L, 5L);
            softly.assertThat(scripted.calls()).isEqualTo(2);
        });
    }

    @Test
    void next_shouldReturnAnEmptyPage_whenTheLastPageHasNoItems() throws IOException {
        final ScriptedTransport scripted =
                ScriptedTransport.replying(page("[{\"id\":1}]", "Link", next(API + "/projects?page=2")), page("[]"));

        final GitLabPage<Item> second;
        try (DefaultGitLabClient client = client(scripted)) {
            second = start(client, "/projects", Map.of(), GitLabPageRequest.byPage())
                    .next()
                    .orElseThrow();
        }

        final Optional<GitLabPage<Item>> afterSecond = second.next();

        assertSoftly(softly -> {
            softly.assertThat(second.items()).isEmpty();
            softly.assertThat(afterSecond).isEmpty();
        });
    }

    @Test
    void totalCount_shouldBeTheTotalOfTheHeader_whenGitLabSendsIt() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[{\"id\":1}]", "X-Total", "42"));

        final GitLabPage<Item> first;
        try (DefaultGitLabClient client = client(scripted)) {
            first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
        }

        assertThat(first.totalCount()).isEqualTo(42);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "-1", "1.5"})
    void totalCount_shouldBeNull_whenTheHeaderIsMissingOrUnusable(final String value) throws IOException {
        final ScriptedTransport withValue = ScriptedTransport.replying(page("[{\"id\":1}]", "X-Total", value));
        final ScriptedTransport without = ScriptedTransport.replying(page("[{\"id\":1}]"));

        final GitLabPage<Item> unusable;
        final GitLabPage<Item> missing;
        try (DefaultGitLabClient client = client(withValue);
                DefaultGitLabClient other = client(without)) {
            unusable = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
            missing = start(other, "/projects", Map.of(), GitLabPageRequest.byPage());
        }

        assertSoftly(softly -> {
            softly.assertThat(unusable.totalCount()).isNull();
            softly.assertThat(missing.totalCount()).isNull();
        });
    }

    @Test
    void next_shouldRefuseAPageTheWalkHasMovedPast_whenAnEarlierPageIsAdvancedAgain() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next(API + "/projects?page=2")),
                page("[{\"id\":2}]", "Link", next(API + "/projects?page=3")),
                page("[{\"id\":3}]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            final GitLabPage<Item> first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
            first.next();
            thrown = catchThrowable(first::next);
        }

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"message\":\"not a list\",\"token\":\"secret\"}", "\"text\"", "42", "null", ""})
    void start_shouldFailWithoutQuotingTheBody_whenTheBodyIsNotAJsonArray(final String body) {
        final ScriptedTransport scripted = ScriptedTransport.replying(page(body));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            thrown = catchThrowable(() -> start(client, "/projects", Map.of(), GitLabPageRequest.byPage()));
        }

        assertThat(thrown)
                .isInstanceOf(PaginationException.class)
                .hasMessageContaining("JSON array")
                .hasMessageNotContaining("secret");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "  [ ]", "\r\n[]"})
    void start_shouldReadNoItems_whenTheBodyIsAnEmptyArrayWithWhitespace(final String body) throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page(body));

        final GitLabPage<Item> first;
        try (DefaultGitLabClient client = client(scripted)) {
            first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
        }

        assertThat(first.items()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[{\"id\":1}]", "  [ {\"id\":1} ]", "\n\t[\r\n{\"id\":1}]"})
    void start_shouldReadTheItem_whenTheBodyIsAnArrayWithLeadingWhitespace(final String body) throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page(body));

        final GitLabPage<Item> first;
        try (DefaultGitLabClient client = client(scripted)) {
            first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
        }

        assertThat(first.items()).containsExactly(new Item(1));
    }

    @Test
    void start_shouldFail_whenTheArrayHoldsAnItemOfTheWrongShape() {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[{\"id\":\"not a number\"}]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            thrown = catchThrowable(() -> start(client, "/projects", Map.of(), GitLabPageRequest.byPage()));
        }

        assertThat(thrown).isInstanceOf(JsonDecodeException.class);
    }

    @Test
    void start_shouldRefuseAPageSizeAboveTheMaximum_whenTheRequestAsksForMoreThanAHundred() {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            thrown = catchThrowable(() -> start(
                    client,
                    "/projects",
                    Map.of(),
                    io.github.piotr_janczukowicz.gitlab.pagination.GitLabOffsetRequest.of(1, 101)));
        }

        assertSoftly(softly -> {
            softly.assertThat(thrown)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("100");
            softly.assertThat(scripted.calls()).isZero();
        });
    }

    @Test
    void start_shouldUseTheExplicitPageSizeOverTheConfiguredOne_whenTheRequestSetsIt() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[]"));

        try (DefaultGitLabClient client =
                client(scripted, PaginationConfiguration.builder().perPage(50).build())) {
            start(
                    client,
                    "/projects",
                    Map.of(),
                    io.github.piotr_janczukowicz.gitlab.pagination.GitLabOffsetRequest.of(3, 10));
        }

        assertThat(scripted.request(0).uri().getRawQuery()).isEqualTo("page=3&per_page=10");
    }

    @Test
    void start_shouldRefuse_whenAnArgumentIsNull() {
        final ScriptedTransport scripted = ScriptedTransport.replying(page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            thrown = catchThrowable(() ->
                    GitLabPagers.start(client, JSON, client.pagination(), null, GitLabPageRequest.byPage(), SPEC));
        }

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void next_shouldExplainThePortDifference_whenTheLinkNamesAnotherPortOfTheSameHost() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next("https://gitlab.example.test:8443/api/v4/projects?page=2")),
                page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            final GitLabPage<Item> first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
            thrown = catchThrowable(first::next);
        }

        assertThat(thrown).isInstanceOf(PaginationException.class).hasMessageContaining("external_url");
    }

    @Test
    void next_shouldRefuseALinkOutsideTheApiPathWithoutAHint_whenTheOriginIsTheSame() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next("https://gitlab.example.test/other/projects?page=2")), page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            final GitLabPage<Item> first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
            thrown = catchThrowable(first::next);
        }

        assertThat(thrown)
                .isInstanceOf(PaginationException.class)
                .hasMessageContaining("outside the API base URI")
                .hasMessageNotContaining("external_url");
    }

    @Test
    void next_shouldFail_whenARelativeLinkCannotBeResolved() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                page("[{\"id\":1}]", "Link", next("//host:99999/api/v4/projects?page=2")), page("[]"));

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            final GitLabPage<Item> first = start(client, "/projects", Map.of(), GitLabPageRequest.byPage());
            thrown = catchThrowable(first::next);
        }

        assertSoftly(softly -> {
            softly.assertThat(thrown)
                    .isInstanceOf(PaginationException.class)
                    .hasMessageContaining("cannot be resolved");
            softly.assertThat(scripted.calls()).isEqualTo(1);
        });
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabPagers> constructor = GitLabPagers.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
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
        return GitLabPagers.start(client, JSON, client.pagination(), target, request, SPEC);
    }

    private static DefaultGitLabClient client(final ScriptedTransport scripted) {
        return client(scripted, PaginationConfiguration.defaults());
    }

    private static DefaultGitLabClient client(
            final ScriptedTransport scripted, final PaginationConfiguration pagination) {
        return client(
                scripted,
                GitLabClientConfiguration.builder()
                        .baseUri(BASE)
                        .pagination(pagination)
                        .build());
    }

    private static DefaultGitLabClient client(
            final ScriptedTransport scripted, final GitLabClientConfiguration configuration) {
        return ClientStacks.assemble(
                        scripted.transport(),
                        AuthenticationProviders.bearer("token"),
                        "gitlab-test/1.0",
                        configuration,
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
