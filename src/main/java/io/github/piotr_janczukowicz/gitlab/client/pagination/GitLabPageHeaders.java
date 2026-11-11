package io.github.piotr_janczukowicz.gitlab.client.pagination;

import io.github.piotr_janczukowicz.gitlab.GitLabHeaders;
import io.github.piotr_janczukowicz.http.HttpResponse;
import org.jspecify.annotations.Nullable;

/** Reads the pagination headers of a response; GitLab leaves some of them out. */
final class GitLabPageHeaders {

    private GitLabPageHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The page that {@code X-Next-Page} names.
     *
     * @return the number, or {@code null} if the header is missing, blank, not a whole number or not positive; GitLab
     *     does not document whether the header is empty or absent on the last page, so both end the walk
     */
    static @Nullable Integer nextPage(final HttpResponse response) {
        final Integer page = wholeNumber(response, GitLabHeaders.NEXT_PAGE);
        return page != null && page > 0 ? page : null;
    }

    /**
     * The total that {@code X-Total} names.
     *
     * @return the number, or {@code null} if the header is missing or unusable
     */
    static @Nullable Integer total(final HttpResponse response) {
        final Integer total = wholeNumber(response, GitLabHeaders.TOTAL);
        return total != null && total >= 0 ? total : null;
    }

    private static @Nullable Integer wholeNumber(final HttpResponse response, final String name) {
        return response.header(name)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(GitLabPageHeaders::parse)
                .orElse(null);
    }

    private static @Nullable Integer parse(final String text) {
        try {
            return Integer.valueOf(text);
        } catch (final NumberFormatException unused) {
            return null;
        }
    }
}
