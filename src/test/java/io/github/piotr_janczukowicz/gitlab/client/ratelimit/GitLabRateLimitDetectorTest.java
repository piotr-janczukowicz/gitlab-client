package io.github.piotr_janczukowicz.gitlab.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.HttpRequest;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.transport.RateLimitSignal;
import io.github.piotr_janczukowicz.http.uri.ParsedUrl;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabRateLimitDetectorTest {

    private static final Instant NOW = Instant.parse("2024-01-05T11:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String RESET_IN_THIRTY =
            Long.toString(NOW.plusSeconds(30).getEpochSecond());
    private static final String RESET_IN_FIFTY_MINUTES =
            Long.toString(NOW.plusSeconds(3000).getEpochSecond());

    private static final GitLabRateLimitDetector DETECTOR = GitLabRateLimitDetector.INSTANCE;

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 302, 400, 401, 403, 404, 409, 422, 500, 502, 503})
    void detect_shouldBeEmpty_whenTheStatusIsNotTooManyRequests(final int status) {
        final HttpResponse response =
                response(status, "Retry-After", "30", "RateLimit-Remaining", "0", "RateLimit-Reset", RESET_IN_THIRTY);

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal).isEmpty();
    }

    @Test
    void detect_shouldBeCredentialWideWithBothTimes_whenTheThrottleThatFiredIsExhausted() {
        final HttpResponse response =
                response(429, "Retry-After", "30", "RateLimit-Remaining", "0", "RateLimit-Reset", RESET_IN_THIRTY);

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal).contains(new RateLimitSignal(Duration.ofSeconds(30), NOW.plusSeconds(30), null));
    }

    @Test
    void detect_shouldUseTheResetAlone_whenRetryAfterIsMissingAndTheThrottleIsExhausted() {
        final HttpResponse response = response(429, "RateLimit-Remaining", "0", "RateLimit-Reset", RESET_IN_THIRTY);

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal).contains(new RateLimitSignal(null, NOW.plusSeconds(30), null));
    }

    @Test
    void detect_shouldIgnoreAFarResetAndScopeTheLimitToTheRequest_whenTheHeadersShowQuotaLeft() {
        final HttpResponse response = response(
                429, "Retry-After", "30", "RateLimit-Remaining", "1500", "RateLimit-Reset", RESET_IN_FIFTY_MINUTES);
        final HttpRequest request = request(HttpMethod.POST, "/projects/7/issues");

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, request, CLOCK);

        assertThat(signal).contains(new RateLimitSignal(Duration.ofSeconds(30), null, DETECTOR.bucketOf(request)));
    }

    @Test
    void detect_shouldScopeTheLimitToTheRequest_whenThereAreNoRateLimitHeaders() {
        final HttpResponse response = response(429, "Retry-After", "86400");
        final HttpRequest request = request(HttpMethod.POST, "/projects");

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, request, CLOCK);

        assertThat(signal).contains(new RateLimitSignal(Duration.ofSeconds(86_400), null, DETECTOR.bucketOf(request)));
    }

    @Test
    void detect_shouldAskForNothingSpecific_whenThereIsNoUsableHeaderAtAll() {
        final HttpResponse response = response(429);
        final HttpRequest request = request(HttpMethod.POST, "/projects");

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, request, CLOCK);

        assertThat(signal).contains(new RateLimitSignal(null, null, DETECTOR.bucketOf(request)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "abc", "", "  ", "1.5", "99999999999999999999"})
    void detect_shouldTreatRetryAfterAsAbsent_whenItIsNotAPositiveWholeNumberOfSeconds(final String retryAfter) {
        final HttpResponse response = response(429, "Retry-After", retryAfter);

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal)
                .hasValueSatisfying(value -> assertThat(value.retryAfter()).isNull());
    }

    @Test
    void detect_shouldIgnoreAResetThatHasPassed_whenTheThrottleIsExhausted() {
        final String past = Long.toString(NOW.minusSeconds(60).getEpochSecond());
        final HttpResponse response = response(429, "RateLimit-Remaining", "0", "RateLimit-Reset", past);

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal).contains(new RateLimitSignal(null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "-1", "1e3"})
    void detect_shouldTreatRemainingAsAbsent_whenItIsNotAWholeNumber(final String remaining) {
        final HttpResponse response = response(
                429, "Retry-After", "10", "RateLimit-Remaining", remaining, "RateLimit-Reset", RESET_IN_THIRTY);
        final HttpRequest request = request(HttpMethod.GET, "/projects");

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, request, CLOCK);

        assertThat(signal).contains(new RateLimitSignal(Duration.ofSeconds(10), null, DETECTOR.bucketOf(request)));
    }

    @Test
    void detect_shouldBeCredentialWide_whenTheRequestIsNotKnown() {
        final HttpResponse response = response(429, "Retry-After", "30");

        final Optional<RateLimitSignal> signal = DETECTOR.detect(response, CLOCK);

        assertThat(signal).contains(new RateLimitSignal(Duration.ofSeconds(30), null, null));
    }

    @Test
    void detectExhaustion_shouldHoldUntilTheReset_whenASuccessfulResponseHasNothingLeft() {
        final HttpResponse response = response(200, "RateLimit-Remaining", "0", "RateLimit-Reset", RESET_IN_THIRTY);

        final Optional<RateLimitSignal> signal =
                DETECTOR.detectExhaustion(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal).contains(new RateLimitSignal(null, NOW.plusSeconds(30), null));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 204, 302})
    void detectExhaustion_shouldApplyToSuccessAndRedirectStatuses(final int status) {
        final HttpResponse response = response(status, "RateLimit-Remaining", "0", "RateLimit-Reset", RESET_IN_THIRTY);

        final Optional<RateLimitSignal> signal =
                DETECTOR.detectExhaustion(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal).isPresent();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 404, 429, 500})
    void detectExhaustion_shouldBeEmpty_whenTheStatusIsAnError(final int status) {
        final HttpResponse response = response(status, "RateLimit-Remaining", "0", "RateLimit-Reset", RESET_IN_THIRTY);

        final Optional<RateLimitSignal> signal =
                DETECTOR.detectExhaustion(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal).isEmpty();
    }

    @Test
    void detectExhaustion_shouldBeEmpty_whenSomeQuotaRemains() {
        final HttpResponse response = response(200, "RateLimit-Remaining", "5", "RateLimit-Reset", RESET_IN_THIRTY);

        final Optional<RateLimitSignal> signal =
                DETECTOR.detectExhaustion(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal).isEmpty();
    }

    @Test
    void detectExhaustion_shouldBeEmpty_whenTheResetHasPassedOrIsMissing() {
        final String past = Long.toString(NOW.minusSeconds(1).getEpochSecond());
        final HttpResponse passed = response(200, "RateLimit-Remaining", "0", "RateLimit-Reset", past);
        final HttpResponse missing = response(200, "RateLimit-Remaining", "0");
        final HttpRequest request = request(HttpMethod.GET, "/projects");

        final Optional<RateLimitSignal> afterPassed = DETECTOR.detectExhaustion(passed, request, CLOCK);
        final Optional<RateLimitSignal> afterMissing = DETECTOR.detectExhaustion(missing, request, CLOCK);

        assertSoftly(softly -> {
            softly.assertThat(afterPassed).isEmpty();
            softly.assertThat(afterMissing).isEmpty();
        });
    }

    @Test
    void detectExhaustion_shouldMeasureTheResetAgainstTheServerDate_whenTheResponseHasOne() {
        final HttpResponse response = new HttpResponse(
                200,
                HttpHeaders.of(
                        "Date",
                        "Fri, 05 Jan 2024 12:00:00 GMT",
                        "RateLimit-Remaining",
                        "0",
                        "RateLimit-Reset",
                        RESET_IN_THIRTY),
                HttpBody.absent());

        final Optional<RateLimitSignal> signal =
                DETECTOR.detectExhaustion(response, request(HttpMethod.GET, "/projects"), CLOCK);

        assertThat(signal).isEmpty();
    }

    @Test
    void bucketOf_shouldBeTheSameForTheSameMethodAndPath_andDifferentOtherwise() {
        final String post = DETECTOR.bucketOf(request(HttpMethod.POST, "/projects"));
        final String postAgain = DETECTOR.bucketOf(request(HttpMethod.POST, "/projects"));
        final String get = DETECTOR.bucketOf(request(HttpMethod.GET, "/projects"));
        final String other = DETECTOR.bucketOf(request(HttpMethod.POST, "/groups"));

        assertSoftly(softly -> {
            softly.assertThat(postAgain).isEqualTo(post);
            softly.assertThat(get).isNotEqualTo(post);
            softly.assertThat(other).isNotEqualTo(post);
        });
    }

    @Test
    void bucketOf_shouldIgnoreTheQuery_whenTheSamePathIsRequestedWithDifferentParameters() {
        final String first = DETECTOR.bucketOf(request(HttpMethod.GET, "/projects?page=1"));
        final String second = DETECTOR.bucketOf(request(HttpMethod.GET, "/projects?page=2"));

        assertThat(second).isEqualTo(first);
    }

    @Test
    void detect_shouldRefuse_whenAnArgumentIsNull() {
        final HttpResponse response = response(429);

        final Throwable noResponse = catchThrowable(() -> DETECTOR.detect(null, CLOCK));
        final Throwable noClock = catchThrowable(() -> DETECTOR.detect(response, null));

        assertSoftly(softly -> {
            softly.assertThat(noResponse).isInstanceOf(NullPointerException.class);
            softly.assertThat(noClock).isInstanceOf(NullPointerException.class);
        });
    }

    private static HttpResponse response(final int status, final String... headers) {
        return new HttpResponse(status, HttpHeaders.of(headers), HttpBody.absent());
    }

    private static HttpRequest request(final HttpMethod method, final String path) {
        return new HttpRequest(
                method,
                ParsedUrl.of(URI.create("https://gitlab.example.test/api/v4" + path)),
                HttpHeaders.empty(),
                HttpBody.absent());
    }
}
