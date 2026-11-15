package io.github.piotr_janczukowicz.gitlab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabApiException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabNotFoundException;
import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabRateLimitException;
import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.transport.RateLimitGateHeaders;
import io.github.piotr_janczukowicz.test.http.HttpResponses;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabErrorMapperTest {

    private static final URI URI_OF_CALL = URI.create("https://gitlab.example.test/api/v4/projects/1");

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final GitLabErrorMapper MAPPER = new GitLabErrorMapper(Clock.fixed(NOW, ZoneOffset.UTC));
    private static final String RESET = Long.toString(NOW.plusSeconds(90).getEpochSecond());

    @Test
    void map_shouldReturnTheExceptionOfTheStatus_whenResponseFails() {
        final HttpResponse response = new HttpResponse(
                404,
                HttpHeaders.empty(),
                HttpBody.of("{\"message\":\"404 Project Not Found\"}".getBytes(StandardCharsets.UTF_8)));

        final RuntimeException exception = MAPPER.map(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception).isInstanceOf(GitLabNotFoundException.class).hasMessage("404 Project Not Found");
    }

    @Test
    void redirectFailure_shouldReturnAGenericExceptionWithoutTheLocationHeader_whenRedirectIsRefused() {
        final HttpResponse response = new HttpResponse(
                302,
                HttpHeaders.of("Location", "https://elsewhere.example.test/x", "X-Request-Id", "req-1"),
                HttpBody.absent());

        final RuntimeException exception =
                MAPPER.redirectFailure(response, "redirect refused", "raw text", HttpMethod.GET, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabApiException.class,
                        failure -> assertSoftly(softly -> {
                            softly.assertThat(failure.getClass()).isEqualTo(GitLabApiException.class);
                            softly.assertThat(failure.statusCode()).isEqualTo(302);
                            softly.assertThat(failure).hasMessage("redirect refused");
                            softly.assertThat(failure.rawBody()).contains("raw text");
                            softly.assertThat(failure.headers().get("Location")).isEmpty();
                            softly.assertThat(failure.headers().get("X-Request-Id"))
                                    .contains("req-1");
                            softly.assertThat(failure.method()).contains(HttpMethod.GET);
                            softly.assertThat(failure.uri()).contains(URI_OF_CALL);
                        }));
    }

    @Test
    void map_shouldCarryTheQuotaAndTheRetryTime_whenA429HasRetryAfterAndRateLimitHeaders() {
        final HttpResponse response = response(
                "Retry-After", "30",
                "RateLimit-Limit", "2000",
                "RateLimit-Remaining", "0",
                "RateLimit-Reset", RESET,
                "RateLimit-Name", "throttle_authenticated_api");

        final RuntimeException exception = MAPPER.map(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabRateLimitException.class,
                        failure -> assertSoftly(softly -> {
                            softly.assertThat(failure.retryAt()).contains(NOW.plusSeconds(30));
                            softly.assertThat(failure.throttle()).contains("throttle_authenticated_api");
                            softly.assertThat(failure.rateLimit()).hasValueSatisfying(quota -> {
                                softly.assertThat(quota.limit()).isEqualTo(2000);
                                softly.assertThat(quota.remaining()).isZero();
                                softly.assertThat(quota.resetAt()).isEqualTo(NOW.plusSeconds(90));
                                softly.assertThat(quota.observedAt()).isEqualTo(NOW);
                            });
                            softly.assertThat(failure.isClientSideBlock()).isFalse();
                        }));
    }

    @Test
    void map_shouldRetryAtTheResetTime_whenA429HasNoRetryAfter() {
        final HttpResponse response =
                response("RateLimit-Limit", "2000", "RateLimit-Remaining", "0", "RateLimit-Reset", RESET);

        final RuntimeException exception = MAPPER.map(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabRateLimitException.class,
                        failure -> assertSoftly(softly -> {
                            softly.assertThat(failure.retryAt()).contains(NOW.plusSeconds(90));
                            softly.assertThat(failure.throttle()).isEmpty();
                        }));
    }

    @Test
    void map_shouldRetryAfterTheDelay_whenA429HasOnlyRetryAfter() {
        final HttpResponse response = response("Retry-After", "7");

        final RuntimeException exception = MAPPER.map(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabRateLimitException.class,
                        failure -> assertSoftly(softly -> {
                            softly.assertThat(failure.retryAt()).contains(NOW.plusSeconds(7));
                            softly.assertThat(failure.rateLimit()).isEmpty();
                            softly.assertThat(failure.throttle()).isEmpty();
                        }));
    }

    @Test
    void map_shouldKnowNothingAboutTheRetry_whenA429HasNoRateLimitHeaders() {
        final HttpResponse response = response();

        final RuntimeException exception = MAPPER.map(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabRateLimitException.class,
                        failure -> assertSoftly(softly -> {
                            softly.assertThat(failure.retryAt()).isEmpty();
                            softly.assertThat(failure.rateLimit()).isEmpty();
                        }));
    }

    @Test
    void map_shouldIgnoreARetryAfterThatIsNotPositive_whenA429HasOne() {
        final HttpResponse response = response(
                "Retry-After", "0", "RateLimit-Limit", "1", "RateLimit-Remaining", "0", "RateLimit-Reset", RESET);

        final RuntimeException exception = MAPPER.map(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabRateLimitException.class,
                        failure -> assertThat(failure.retryAt()).contains(NOW.plusSeconds(90)));
    }

    @Test
    void map_shouldReportAClientSideBlock_whenTheGateReplayedTheResponse() {
        final HttpResponse response =
                response("X-Client-Rate-Limit-Gate", RateLimitGateHeaders.BLOCKED, "Retry-After", "5");

        final RuntimeException exception = MAPPER.map(HttpMethod.GET, response, URI_OF_CALL);

        assertThat(exception)
                .isInstanceOfSatisfying(
                        GitLabRateLimitException.class,
                        failure -> assertThat(failure.isClientSideBlock()).isTrue());
    }

    @Test
    void constructor_shouldThrowNullPointerException_whenClockIsNull() {
        final Throwable thrown = catchThrowable(() -> new GitLabErrorMapper(null));

        assertThat(thrown).isInstanceOf(NullPointerException.class).hasMessageContaining("clock");
    }

    private static HttpResponse response(final String... headers) {
        return HttpResponses.response(429, "{\"message\":\"Retry later\"}", headers);
    }
}
