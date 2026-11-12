package io.github.piotr_janczukowicz.gitlab.client.internal;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitListener;
import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitSnapshot;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the quota of the latest response that reported one, and tells the listener of each. GitLab shows only the most
 * restrictive throttle in the headers of a response, so the figures of two responses can belong to different throttles,
 * and the latest one is the one that matters now.
 */
final class GitLabRateLimitTracker {

    private static final Logger LOG = LoggerFactory.getLogger(GitLabRateLimitTracker.class);

    private final @Nullable GitLabRateLimitListener listener;
    private final Clock clock;
    private final AtomicReference<@Nullable GitLabRateLimitSnapshot> latest = new AtomicReference<>();

    GitLabRateLimitTracker(final @Nullable GitLabRateLimitListener listener, final Clock clock) {
        this.listener = listener;
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Records the quota of a response, if its headers carry one.
     *
     * @param headers headers of the response
     */
    void observe(final HttpHeaders headers) {
        GitLabRateLimitSnapshot.from(requireNonNull(headers, "headers"), clock.instant())
                .ifPresent(this::keep);
    }

    Optional<GitLabRateLimitSnapshot> last() {
        return Optional.ofNullable(latest.get());
    }

    private void keep(final GitLabRateLimitSnapshot snapshot) {
        latest.set(snapshot);
        LOG.atDebug()
                .setMessage("Rate limit {}: {} of {} remaining, resets at {}")
                .addArgument(snapshot.name())
                .addArgument(snapshot.remaining())
                .addArgument(snapshot.limit())
                .addArgument(snapshot.resetAt())
                .log();
        if (listener != null) {
            tell(listener, snapshot);
        }
    }

    private static void tell(final GitLabRateLimitListener listener, final GitLabRateLimitSnapshot snapshot) {
        try {
            listener.onRateLimit(snapshot);
        } catch (final RuntimeException failure) {
            LOG.atWarn()
                    .setMessage("Rate-limit listener failed for {}: {}")
                    .addArgument(snapshot.name())
                    .addArgument(failure.toString())
                    .log();
        }
    }
}
