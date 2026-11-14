package io.github.piotr_janczukowicz.gitlab.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class GitLabRateLimitHeadersTest {

    @ParameterizedTest
    @CsvSource({"42,42", "' 7 ',7", "0,0", "-3,-3"})
    void wholeNumber_shouldReadAWholeNumber_whenTheHeaderHoldsOne(final String text, final int expected) {
        final HttpHeaders headers = HttpHeaders.of("X-Count", text);

        final Integer number = GitLabRateLimitHeaders.wholeNumber(headers, "X-Count");

        assertThat(number).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"''", "abc", "1.5", "1e3", "99999999999999999999", "+5"})
    void wholeNumber_shouldBeNull_whenTheHeaderIsNotAUsableWholeNumber(final String text) {
        final HttpHeaders headers = HttpHeaders.of("X-Count", text);

        final Integer number = GitLabRateLimitHeaders.wholeNumber(headers, "X-Count");

        assertThat(number).isNull();
    }

    @Test
    void wholeNumber_shouldBeNull_whenTheHeaderIsMissing() {
        assertThat(GitLabRateLimitHeaders.wholeNumber(HttpHeaders.empty(), "X-Count"))
                .isNull();
    }

    @Test
    void epochSecond_shouldReadAUnixTime_whenTheHeaderHoldsOne() {
        final HttpHeaders headers = HttpHeaders.of("X-Reset", "1609844400");

        final Instant instant = GitLabRateLimitHeaders.epochSecond(headers, "X-Reset");

        assertThat(instant).isEqualTo(Instant.ofEpochSecond(1_609_844_400L));
    }

    @Test
    void epochSecond_shouldReadTheLatestSupportedReset_whenTheHeaderHoldsTheEndOfYear9999() {
        final HttpHeaders headers = HttpHeaders.of("RateLimit-Reset", "253402300799");

        final Instant reset = GitLabRateLimitHeaders.epochSecond(headers, "RateLimit-Reset");

        assertThat(reset).isEqualTo(Instant.parse("9999-12-31T23:59:59Z"));
    }

    @ParameterizedTest
    @CsvSource({
        "''",
        "soon",
        "-5",
        "1.5",
        "99999999999999999999999",
        "253402300800",
        "31556889864403200",
        "9223372036854775807"
    })
    void epochSecond_shouldBeNull_whenTheHeaderIsNotAUsableUnixTime(final String text) {
        final HttpHeaders headers = HttpHeaders.of("X-Reset", text);

        final Instant instant = GitLabRateLimitHeaders.epochSecond(headers, "X-Reset");

        assertThat(instant).isNull();
    }

    @Test
    void epochSecond_shouldBeNull_whenTheHeaderIsMissing() {
        assertThat(GitLabRateLimitHeaders.epochSecond(HttpHeaders.empty(), "X-Reset"))
                .isNull();
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabRateLimitHeaders> constructor = GitLabRateLimitHeaders.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertSoftly(softly -> softly.assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class));
    }
}
