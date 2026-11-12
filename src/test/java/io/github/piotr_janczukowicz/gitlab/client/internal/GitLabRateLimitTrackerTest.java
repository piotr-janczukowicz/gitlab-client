package io.github.piotr_janczukowicz.gitlab.client.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitListener;
import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitSnapshot;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabRateLimitTrackerTest {

    private static final Instant NOW = Instant.parse("2024-01-05T11:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void last_shouldBeEmpty_whenNothingWasObserved() {
        final GitLabRateLimitTracker tracker = new GitLabRateLimitTracker(null, CLOCK);

        assertThat(tracker.last()).isEmpty();
    }

    @Test
    void observe_shouldKeepTheSnapshotWithTheTimeOfObservation_whenHeadersAreComplete() {
        final GitLabRateLimitTracker tracker = new GitLabRateLimitTracker(null, CLOCK);

        tracker.observe(headers("60", "33", "1609844400", "throttle_authenticated_api"));

        assertThat(tracker.last())
                .contains(new GitLabRateLimitSnapshot(
                        60, 33, Instant.ofEpochSecond(1_609_844_400L), "throttle_authenticated_api", NOW));
    }

    @Test
    void observe_shouldReplaceTheSnapshot_whenAnotherResponseArrives() {
        final GitLabRateLimitTracker tracker = new GitLabRateLimitTracker(null, CLOCK);
        tracker.observe(headers("60", "33", "1609844400", "throttle_authenticated_api"));

        tracker.observe(headers("10", "9", "1609844500", "throttle_protected_paths"));

        assertThat(tracker.last())
                .hasValueSatisfying(snapshot -> assertSoftly(softly -> {
                    softly.assertThat(snapshot.name()).isEqualTo("throttle_protected_paths");
                    softly.assertThat(snapshot.remaining()).isEqualTo(9);
                }));
    }

    @Test
    void observe_shouldKeepThePreviousSnapshot_whenTheResponseHasNoRateLimitHeaders() {
        final GitLabRateLimitTracker tracker = new GitLabRateLimitTracker(null, CLOCK);
        tracker.observe(headers("60", "33", "1609844400", null));

        tracker.observe(HttpHeaders.empty());

        assertThat(tracker.last())
                .hasValueSatisfying(snapshot -> assertThat(snapshot.remaining()).isEqualTo(33));
    }

    @Test
    void observe_shouldTellTheListenerOfEverySnapshot_whenOneIsConfigured() {
        final List<GitLabRateLimitSnapshot> told = new ArrayList<>();
        final GitLabRateLimitTracker tracker = new GitLabRateLimitTracker(told::add, CLOCK);

        tracker.observe(headers("60", "33", "1609844400", null));
        tracker.observe(headers("60", "32", "1609844400", null));
        tracker.observe(HttpHeaders.empty());

        assertThat(told).extracting(GitLabRateLimitSnapshot::remaining).containsExactly(33, 32);
    }

    @Test
    void observe_shouldKeepWorkingAndLogTheFailure_whenTheListenerThrows() {
        final GitLabRateLimitListener failing = snapshot -> {
            throw new IllegalStateException("listener failed");
        };
        final GitLabRateLimitTracker tracker = new GitLabRateLimitTracker(failing, CLOCK);

        try (LogCaptor logs = LogCaptor.forClass(GitLabRateLimitTracker.class)) {
            final Throwable thrown = catchThrowable(() -> tracker.observe(headers("60", "33", "1609844400", null)));

            assertSoftly(softly -> {
                softly.assertThat(thrown).isNull();
                softly.assertThat(tracker.last()).isPresent();
                softly.assertThat(logs.getWarnLogs()).hasSize(1);
                softly.assertThat(logs.getWarnLogs().getFirst()).contains("listener failed");
            });
        }
    }

    @Test
    void constructor_shouldRefuse_whenTheClockIsNull() {
        final Throwable thrown = catchThrowable(() -> new GitLabRateLimitTracker(null, null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void observe_shouldRefuse_whenTheHeadersAreNull() {
        final GitLabRateLimitTracker tracker = new GitLabRateLimitTracker(null, CLOCK);

        final Throwable thrown = catchThrowable(() -> tracker.observe(null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    private static HttpHeaders headers(
            final String limit, final String remaining, final String reset, final String name) {
        final List<String> pairs = new ArrayList<>(
                List.of("RateLimit-Limit", limit, "RateLimit-Remaining", remaining, "RateLimit-Reset", reset));
        if (name != null) {
            pairs.addAll(List.of("RateLimit-Name", name));
        }
        return HttpHeaders.of(pairs.toArray(String[]::new));
    }
}
