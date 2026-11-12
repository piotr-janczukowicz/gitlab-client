package io.github.piotr_janczukowicz.gitlab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.GitLabClientConfiguration;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabRateLimitException;
import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitDetector;
import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitSnapshot;
import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.http.transport.RateLimitDetector;
import io.github.piotr_janczukowicz.test.http.ScriptedTransport;
import io.github.piotr_janczukowicz.test.http.TestRetryingTransports;
import io.github.piotr_janczukowicz.testsupport.MutableClock;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.io.IOException;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

@UnitTest
class ClientStacksRateLimitTest {

    private static final URI BASE = URI.create("https://gitlab.example.test");
    private static final Instant NOW = Instant.parse("2024-01-05T11:00:00Z");

    private final List<Duration> sleeps = new ArrayList<>();
    private final MutableClock clock = new MutableClock(NOW);

    @Test
    void assemble_shouldInstallTheGitLabDetectorInTheRetryLayer_whenTheStackIsBuilt() {
        final AtomicReference<RateLimitDetector> installed = new AtomicReference<>();
        final ScriptedTransport scripted = ScriptedTransport.replying(ok());

        try (DefaultGitLabClient client = ClientStacks.assemble(
                        scripted.transport(),
                        AuthenticationProviders.bearer("token"),
                        "gitlab-test/1.0",
                        GitLabClientConfiguration.builder().baseUri(BASE).build(),
                        (transport, retry) -> {
                            installed.set(retry.rateLimitDetector());
                            return transport;
                        })
                .build()) {
            assertThat(installed.get()).isSameAs(GitLabRateLimitDetector.INSTANCE);
        }
    }

    @Test
    void execute_shouldWaitForRetryAfterAndSucceed_whenTheThrottleThatFiredIsExhausted() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                throttled("Retry-After", "2", "RateLimit-Remaining", "0", "RateLimit-Reset", reset(2)), ok());

        final HttpResponse response;
        try (DefaultGitLabClient client = client(scripted)) {
            response = client.execute(HttpCall.get("/projects/1"));
        }

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(200);
            softly.assertThat(scripted.calls()).isEqualTo(2);
            softly.assertThat(sleeps).hasSize(1);
            softly.assertThat(sleeps.getFirst()).isBetween(Duration.ofSeconds(2), Duration.ofSeconds(30));
        });
    }

    @Test
    void execute_shouldWaitTheDefaultOfAMinute_whenThe429HasNoUsableHeader() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(throttled(), ok());

        final HttpResponse response;
        try (DefaultGitLabClient client = client(scripted)) {
            response = client.execute(HttpCall.get("/projects/1"));
        }

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(200);
            softly.assertThat(sleeps).hasSize(1);
            softly.assertThat(sleeps.getFirst()).isBetween(Duration.ofSeconds(30), Duration.ofSeconds(90));
        });
    }

    @Test
    void execute_shouldNotHoldOtherRequests_whenAnApplicationLimitAsksForADayOnOnePath() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(throttled("Retry-After", "86400"), ok(), ok());

        final Throwable creation;
        final HttpResponse read;
        final Throwable creationAgain;
        try (DefaultGitLabClient client = client(scripted)) {
            creation = catchThrowable(() -> client.execute(HttpCall.post("/projects")));
            read = client.execute(HttpCall.get("/projects/1"));
            creationAgain = catchThrowable(() -> client.execute(HttpCall.post("/projects")));
        }

        assertSoftly(softly -> {
            softly.assertThat(creation).isInstanceOf(GitLabRateLimitException.class);
            softly.assertThat(read.statusCode()).isEqualTo(200);
            softly.assertThat(creationAgain).isInstanceOf(GitLabRateLimitException.class);
            softly.assertThat(scripted.calls()).isEqualTo(2);
            softly.assertThat(sleeps).isEmpty();
        });
    }

    @Test
    void execute_shouldFailFastForEveryRequest_whenTheThrottleThatFiredAsksForMoreThanTheCap() {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                throttled("Retry-After", "3600", "RateLimit-Remaining", "0", "RateLimit-Reset", reset(3600)), ok());

        final Throwable first;
        final Throwable second;
        try (DefaultGitLabClient client = client(scripted)) {
            first = catchThrowable(() -> client.execute(HttpCall.get("/projects/1")));
            second = catchThrowable(() -> client.execute(HttpCall.get("/groups/2")));
        }

        assertSoftly(softly -> {
            softly.assertThat(first).isInstanceOf(GitLabRateLimitException.class);
            softly.assertThat(second).isInstanceOf(GitLabRateLimitException.class);
            softly.assertThat(scripted.calls()).isEqualTo(1);
            softly.assertThat(sleeps).isEmpty();
        });
    }

    @Test
    void execute_shouldHoldTheNextCallUntilTheReset_whenASuccessfulResponseUsedUpTheQuota() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                ok("RateLimit-Limit", "60", "RateLimit-Remaining", "0", "RateLimit-Reset", reset(30)), ok());

        final HttpResponse second;
        try (DefaultGitLabClient client = client(scripted)) {
            client.execute(HttpCall.get("/projects/1"));
            second = client.execute(HttpCall.get("/projects/2"));
        }

        assertSoftly(softly -> {
            softly.assertThat(second.statusCode()).isEqualTo(200);
            softly.assertThat(scripted.calls()).isEqualTo(2);
            softly.assertThat(sleeps).hasSize(1);
            softly.assertThat(sleeps.getFirst()).isBetween(Duration.ofSeconds(29), Duration.ofSeconds(60));
        });
    }

    @Test
    void execute_shouldRefuseTheNextCallWithARateLimitException_whenTheQuotaResetsAfterTheCap() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                ok("RateLimit-Limit", "60", "RateLimit-Remaining", "0", "RateLimit-Reset", reset(3600)), ok());

        final Throwable second;
        try (DefaultGitLabClient client = client(scripted)) {
            client.execute(HttpCall.get("/projects/1"));
            second = catchThrowable(() -> client.execute(HttpCall.get("/projects/2")));
        }

        assertSoftly(softly -> {
            softly.assertThat(second).isInstanceOf(GitLabRateLimitException.class);
            softly.assertThat(scripted.calls()).isEqualTo(1);
        });
    }

    @Test
    void execute_shouldNeverTreatAForbiddenAsARateLimit_whenItCarriesRetryAfter() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                new HttpResponse(
                        403,
                        HttpHeaders.of("Retry-After", "30", "RateLimit-Remaining", "0", "RateLimit-Reset", reset(30)),
                        HttpBody.absent()),
                ok());

        final Throwable first;
        final HttpResponse second;
        try (DefaultGitLabClient client = client(scripted)) {
            first = catchThrowable(() -> client.execute(HttpCall.get("/projects/1")));
            second = client.execute(HttpCall.get("/projects/1"));
        }

        assertSoftly(softly -> {
            softly.assertThat(first).hasMessageContaining("403");
            softly.assertThat(second.statusCode()).isEqualTo(200);
            softly.assertThat(sleeps).isEmpty();
        });
    }

    @Test
    void lastRateLimit_shouldReportTheQuotaOfTheLatestResponseAndTellTheListener_whenResponsesCarryHeaders()
            throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(ok(
                "RateLimit-Limit", "2000",
                "RateLimit-Remaining", "1999",
                "RateLimit-Reset", reset(60),
                "RateLimit-Name", "throttle_authenticated_api"));
        final List<GitLabRateLimitSnapshot> told = new ArrayList<>();
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(BASE)
                .rateLimitListener(told::add)
                .build();

        try (DefaultGitLabClient client = client(scripted, configuration)) {
            final var before = client.lastRateLimit();
            client.execute(HttpCall.get("/projects/1"));

            assertSoftly(softly -> {
                softly.assertThat(before).isEmpty();
                softly.assertThat(client.lastRateLimit()).hasValueSatisfying(snapshot -> {
                    softly.assertThat(snapshot.limit()).isEqualTo(2000);
                    softly.assertThat(snapshot.remaining()).isEqualTo(1999);
                    softly.assertThat(snapshot.name()).isEqualTo("throttle_authenticated_api");
                });
                softly.assertThat(told).hasSize(1);
            });
        }
    }

    @Test
    void execute_shouldReturnTheResponse_whenASuccessCarriesAResetBeyondTheInstantRange() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(ok(
                "RateLimit-Limit", "1", "RateLimit-Remaining", "0", "RateLimit-Reset", Long.toString(Long.MAX_VALUE)));

        final HttpResponse response;
        try (DefaultGitLabClient client = client(scripted)) {
            response = client.execute(HttpCall.get("/projects/1"));
        }

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void execute_shouldRetryAndSucceed_whenAThrottleCarriesAResetBeyondTheInstantRange() throws IOException {
        final ScriptedTransport scripted = ScriptedTransport.replying(
                throttled("RateLimit-Remaining", "0", "RateLimit-Reset", Long.toString(Long.MAX_VALUE)), ok());

        final HttpResponse response;
        try (DefaultGitLabClient client = client(scripted)) {
            response = client.execute(HttpCall.get("/projects/1"));
        }

        assertSoftly(softly -> {
            softly.assertThat(response.statusCode()).isEqualTo(200);
            softly.assertThat(sleeps).hasSize(1);
        });
    }

    @Test
    void execute_shouldNotFailWithADateTimeException_whenRetryAfterIsTheLargestWholeNumber() {
        final ScriptedTransport scripted =
                ScriptedTransport.replying(throttled("Retry-After", Long.toString(Long.MAX_VALUE)), ok());

        final Throwable thrown;
        try (DefaultGitLabClient client = client(scripted)) {
            thrown = catchThrowable(() -> client.execute(HttpCall.get("/projects/1")));
        }

        assertThat(thrown).isNotInstanceOf(DateTimeException.class);
    }

    private DefaultGitLabClient client(final ScriptedTransport scripted) {
        return client(
                scripted, GitLabClientConfiguration.builder().baseUri(BASE).build());
    }

    private DefaultGitLabClient client(
            final ScriptedTransport scripted, final GitLabClientConfiguration configuration) {
        return ClientStacks.assemble(
                        scripted.transport(),
                        AuthenticationProviders.bearer("token"),
                        "gitlab-test/1.0",
                        configuration,
                        (transport, retry) -> TestRetryingTransports.create(
                                transport, retry, TestRetryingTransports.recordingAndAdvancing(sleeps, clock), clock))
                .build();
    }

    private static String reset(final long seconds) {
        return Long.toString(NOW.plusSeconds(seconds).getEpochSecond());
    }

    private static HttpResponse ok(final String... headers) {
        return new HttpResponse(200, HttpHeaders.of(headers), HttpBody.absent());
    }

    private static HttpResponse throttled(final String... headers) {
        return new HttpResponse(429, HttpHeaders.of(headers), HttpBody.absent());
    }
}
