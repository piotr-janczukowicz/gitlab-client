package io.github.piotr_janczukowicz.gitlab.client.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class GitLabRateLimitSnapshotTest {

    private static final Instant NOW = Instant.parse("2024-01-05T11:00:00Z");

    @Test
    void from_shouldReadTheDocumentedHeaders_whenTheyAreAllPresent() {
        final HttpHeaders headers = HttpHeaders.of(
                "RateLimit-Limit", "60",
                "RateLimit-Remaining", "33",
                "RateLimit-Observed", "67",
                "RateLimit-Reset", "1609844400",
                "RateLimit-Name", "throttle_authenticated_api");

        final Optional<GitLabRateLimitSnapshot> snapshot = GitLabRateLimitSnapshot.from(headers, NOW);

        assertThat(snapshot)
                .hasValueSatisfying(value -> assertSoftly(softly -> {
                    softly.assertThat(value.limit()).isEqualTo(60);
                    softly.assertThat(value.remaining()).isEqualTo(33);
                    softly.assertThat(value.resetAt()).isEqualTo(Instant.ofEpochSecond(1_609_844_400L));
                    softly.assertThat(value.name()).isEqualTo("throttle_authenticated_api");
                    softly.assertThat(value.observedAt()).isEqualTo(NOW);
                }));
    }

    @Test
    void from_shouldLeaveTheNameEmpty_whenThatHeaderIsMissingOrBlank() {
        final HttpHeaders missing =
                HttpHeaders.of("RateLimit-Limit", "60", "RateLimit-Remaining", "1", "RateLimit-Reset", "1609844400");
        final HttpHeaders blank = HttpHeaders.of(
                "RateLimit-Limit", "60",
                "RateLimit-Remaining", "1",
                "RateLimit-Reset", "1609844400",
                "RateLimit-Name", "   ");

        final Optional<GitLabRateLimitSnapshot> withoutName = GitLabRateLimitSnapshot.from(missing, NOW);
        final Optional<GitLabRateLimitSnapshot> blankName = GitLabRateLimitSnapshot.from(blank, NOW);

        assertSoftly(softly -> {
            softly.assertThat(withoutName)
                    .hasValueSatisfying(value -> assertThat(value.name()).isNull());
            softly.assertThat(blankName)
                    .hasValueSatisfying(value -> assertThat(value.name()).isNull());
        });
    }

    @Test
    void from_shouldBeEmpty_whenTheResponseCarriesNoRateLimitHeaders() {
        final Optional<GitLabRateLimitSnapshot> snapshot = GitLabRateLimitSnapshot.from(HttpHeaders.empty(), NOW);

        assertThat(snapshot).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "'',1,1609844400",
        "60,'',1609844400",
        "60,1,''",
        "abc,1,1609844400",
        "60,x,1609844400",
        "60,1,soon",
        "-1,1,1609844400",
        "60,-1,1609844400",
        "60.5,1,1609844400",
        "60,1,-5"
    })
    void from_shouldBeEmpty_whenARequiredHeaderIsMissingOrUnusable(
            final String limit, final String remaining, final String reset) {
        final HttpHeaders headers =
                HttpHeaders.of("RateLimit-Limit", limit, "RateLimit-Remaining", remaining, "RateLimit-Reset", reset);

        final Optional<GitLabRateLimitSnapshot> snapshot = GitLabRateLimitSnapshot.from(headers, NOW);

        assertThat(snapshot).isEmpty();
    }

    @Test
    void isExhausted_shouldBeTrueOnlyWhenNothingRemains() {
        final GitLabRateLimitSnapshot none = new GitLabRateLimitSnapshot(60, 0, NOW.plusSeconds(30), null, NOW);
        final GitLabRateLimitSnapshot some = new GitLabRateLimitSnapshot(60, 1, NOW.plusSeconds(30), null, NOW);

        assertSoftly(softly -> {
            softly.assertThat(none.isExhausted()).isTrue();
            softly.assertThat(some.isExhausted()).isFalse();
        });
    }

    @Test
    void untilReset_shouldBeTheTimeLeft_whenTheResetIsInTheFuture() {
        final GitLabRateLimitSnapshot snapshot = new GitLabRateLimitSnapshot(60, 5, NOW.plusSeconds(42), null, NOW);

        final Duration left = snapshot.untilReset(NOW);

        assertThat(left).isEqualTo(Duration.ofSeconds(42));
    }

    @Test
    void untilReset_shouldBeZero_whenTheResetHasPassed() {
        final GitLabRateLimitSnapshot snapshot = new GitLabRateLimitSnapshot(60, 5, NOW.minusSeconds(10), null, NOW);

        final Duration left = snapshot.untilReset(NOW);

        assertThat(left).isEqualTo(Duration.ZERO);
    }

    @Test
    void constructor_shouldRefuse_whenALimitIsNegativeOrAnInstantIsNull() {
        final Throwable negative = catchThrowable(() -> new GitLabRateLimitSnapshot(-1, 0, NOW, null, NOW));
        final Throwable remaining = catchThrowable(() -> new GitLabRateLimitSnapshot(1, -1, NOW, null, NOW));
        final Throwable noReset = catchThrowable(() -> new GitLabRateLimitSnapshot(1, 1, null, null, NOW));
        final Throwable noObserved = catchThrowable(() -> new GitLabRateLimitSnapshot(1, 1, NOW, null, null));

        assertSoftly(softly -> {
            softly.assertThat(negative).isInstanceOf(IllegalArgumentException.class);
            softly.assertThat(remaining).isInstanceOf(IllegalArgumentException.class);
            softly.assertThat(noReset).isInstanceOf(NullPointerException.class);
            softly.assertThat(noObserved).isInstanceOf(NullPointerException.class);
        });
    }

    @Test
    void untilReset_shouldRefuse_whenNowIsNull() {
        final GitLabRateLimitSnapshot snapshot = new GitLabRateLimitSnapshot(60, 5, NOW, null, NOW);

        final Throwable thrown = catchThrowable(() -> snapshot.untilReset(null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void from_shouldRefuse_whenAnArgumentIsNull() {
        final Throwable noHeaders = catchThrowable(() -> GitLabRateLimitSnapshot.from(null, NOW));
        final Throwable noInstant = catchThrowable(() -> GitLabRateLimitSnapshot.from(HttpHeaders.empty(), null));

        assertSoftly(softly -> {
            softly.assertThat(noHeaders).isInstanceOf(NullPointerException.class);
            softly.assertThat(noInstant).isInstanceOf(NullPointerException.class);
        });
    }
}
