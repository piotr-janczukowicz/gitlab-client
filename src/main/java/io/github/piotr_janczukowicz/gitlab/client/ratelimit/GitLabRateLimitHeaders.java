package io.github.piotr_janczukowicz.gitlab.client.ratelimit;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.util.DateTimeUtils;
import io.github.piotr_janczukowicz.util.NumberUtils;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/** Reads the numbers of GitLab's rate-limit headers, which are plain whole numbers. */
final class GitLabRateLimitHeaders {

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
        final OptionalInt number = NumberUtils.parseInt(headers.get(name).orElse(null));
        return number.isPresent() ? number.getAsInt() : null;
    }

    /**
     * A header that holds a Unix time in seconds.
     *
     * @return the instant, or {@code null} if the header is missing, not a whole number, negative or later than the
     *     year 9999, which no server means as a reset and which {@link Instant} may not represent
     */
    static @Nullable Instant epochSecond(final HttpHeaders headers, final String name) {
        final OptionalLong seconds = NumberUtils.parseLong(headers.get(name).orElse(null));
        return seconds.isEmpty()
                ? null
                : DateTimeUtils.ofEpochSecond(seconds.getAsLong()).orElse(null);
    }
}
