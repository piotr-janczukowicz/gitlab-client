package io.github.piotr_janczukowicz.gitlab.client.ratelimit;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Reads the numbers of GitLab's rate-limit headers, which are plain whole numbers. */
final class GitLabRateLimitHeaders {

    /** 9999-12-31T23:59:59Z, the latest reset that is read. */
    private static final long LATEST_RESET_EPOCH_SECOND = 253_402_300_799L;

    private GitLabRateLimitHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * A header that holds a whole number.
     *
     * @return the number, which may be negative, or {@code null} if the header is missing, blank, not a whole number or
     *     too large
     */
    static @Nullable Integer wholeNumber(final HttpHeaders headers, final String name) {
        return headers.get(name).map(GitLabRateLimitHeaders::parseInt).orElse(null);
    }

    /**
     * A header that holds a Unix time in seconds.
     *
     * @return the instant, or {@code null} if the header is missing, not a whole number, negative or later than the
     *     year 9999, which no server means as a reset and which {@link Instant} may not represent
     */
    static @Nullable Instant epochSecond(final HttpHeaders headers, final String name) {
        return headers.get(name)
                .map(GitLabRateLimitHeaders::parseLong)
                .filter(seconds -> seconds >= 0 && seconds <= LATEST_RESET_EPOCH_SECOND)
                .map(Instant::ofEpochSecond)
                .orElse(null);
    }

    private static @Nullable Integer parseInt(final String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (final NumberFormatException unused) {
            return null;
        }
    }

    private static @Nullable Long parseLong(final String text) {
        try {
            return Long.valueOf(text.trim());
        } catch (final NumberFormatException unused) {
            return null;
        }
    }
}
