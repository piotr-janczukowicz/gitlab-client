package io.github.piotr_janczukowicz.gitlab.client.internal;

import static io.github.piotr_janczukowicz.http.HttpHeaderNames.ETAG;
import static io.github.piotr_janczukowicz.http.HttpHeaderNames.IF_NONE_MATCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.GitLabHeaders;
import io.github.piotr_janczukowicz.gitlab.client.GitLabClientConfiguration;
import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProvider;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.http.transport.HttpTransport;
import io.github.piotr_janczukowicz.test.http.ScriptedTransport;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@UnitTest
class ClientStacksTest {

    private static final URI BASE = URI.create("https://gitlab.example.test");
    private static final String ETAG_VALUE = "W/\"user-1\"";

    @Test
    void assemble_shouldNotShareCachedResponses_whenSudoDiffers() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(cacheable("alice"), cacheable("bob"));

        try (DefaultGitLabClient client = client(scripted)) {
            client.execute(HttpCall.get("/user").withHeader(GitLabHeaders.SUDO, "alice"));
            client.execute(HttpCall.get("/user").withHeader(GitLabHeaders.SUDO, "bob"));
        }

        assertThat(scripted.request(1).headers().get(IF_NONE_MATCH)).isEmpty();
    }

    @Test
    void assemble_shouldRevalidateTheCachedResponse_whenSudoIsTheSame() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(cacheable("alice"), notModified());

        try (DefaultGitLabClient client = client(scripted)) {
            client.execute(HttpCall.get("/user").withHeader(GitLabHeaders.SUDO, "alice"));
            client.execute(HttpCall.get("/user").withHeader(GitLabHeaders.SUDO, "alice"));
        }

        assertThat(scripted.request(1).headers().get(IF_NONE_MATCH)).contains(ETAG_VALUE);
    }

    @Test
    void assemble_shouldNotShareCachedResponses_whenSudoIsAbsentOnOneRequest() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(cacheable("alice"), cacheable("admin"));

        try (DefaultGitLabClient client = client(scripted)) {
            client.execute(HttpCall.get("/user").withHeader(GitLabHeaders.SUDO, "alice"));
            client.execute(HttpCall.get("/user"));
        }

        assertThat(scripted.request(1).headers().get(IF_NONE_MATCH)).isEmpty();
    }

    @Test
    void assemble_shouldNotShareCachedResponses_whenAcceptDiffers() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(cacheable("json"), cacheable("text"));

        try (DefaultGitLabClient client = client(scripted)) {
            client.execute(HttpCall.get("/user").withHeader("Accept", "application/json"));
            client.execute(HttpCall.get("/user").withHeader("Accept", "text/plain"));
        }

        assertThat(scripted.request(1).headers().get(IF_NONE_MATCH)).isEmpty();
    }

    private static DefaultGitLabClient client(final ScriptedTransport scripted) {
        final GitLabClientConfiguration configuration =
                GitLabClientConfiguration.builder().baseUri(BASE).build();
        return ClientStacks.assemble(
                        scripted.transport(),
                        AuthenticationProviders.bearer("token"),
                        "gitlab-test/1.0",
                        configuration,
                        (transport, retry) -> transport)
                .build();
    }

    private static HttpResponse cacheable(final String body) {
        return new HttpResponse(
                200, HttpHeaders.of(ETAG, ETAG_VALUE), HttpBody.of(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static HttpResponse notModified() {
        return new HttpResponse(304, HttpHeaders.of(ETAG, ETAG_VALUE), HttpBody.absent());
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<ClientStacks> constructor = ClientStacks.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void assemble_shouldReportEachCallToTheObserver_whenOneIsConfigured() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(cacheable("alice"));
        final AtomicInteger observed = new AtomicInteger();
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(BASE)
                .callObserver(event -> observed.incrementAndGet())
                .build();

        try (DefaultGitLabClient client = client(scripted, configuration)) {
            client.execute(HttpCall.get("/user"));
        }

        assertThat(observed).hasValue(1);
    }

    @Test
    void assemble_shouldCloseTheBaseTransportAndRethrow_whenTheUserAgentIsInvalid() {
        final ScriptedTransport scripted = ScriptedTransport.replying(cacheable("alice"));
        final HttpTransport base = scripted.transport();
        final GitLabClientConfiguration configuration =
                GitLabClientConfiguration.builder().baseUri(BASE).build();
        final AuthenticationProvider auth = AuthenticationProviders.bearer("token");

        final Throwable thrown = catchThrowable(
                () -> ClientStacks.assemble(base, auth, " ", configuration, (transport, retry) -> transport));

        assertSoftly(softly -> {
            softly.assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            softly.assertThat(scripted.isClosed()).isTrue();
        });
    }

    @Test
    void assemble_shouldPaceRequestsThatChangeState_whenAMutationIntervalIsSet() throws IOException {
        final ScriptedTransport scripted =
                ScriptedTransport.replying(new HttpResponse(200, HttpHeaders.empty(), HttpBody.absent()));
        final Duration interval = Duration.ofMillis(200);
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(BASE)
                .mutationInterval(interval)
                .build();

        try (DefaultGitLabClient client = client(scripted, configuration)) {
            client.execute(HttpCall.post("/projects"));
            client.execute(HttpCall.post("/projects"));
        }

        final List<Long> starts = scripted.startNanos();
        assertThat(Duration.ofNanos(starts.get(1) - starts.get(0))).isGreaterThanOrEqualTo(Duration.ofMillis(150));
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
}
