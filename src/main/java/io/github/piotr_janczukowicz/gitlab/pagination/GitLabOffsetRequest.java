package io.github.piotr_janczukowicz.gitlab.pagination;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireAtLeast;

import java.util.Map;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * A request for a page by number, GitLab's offset pagination (<a href="https://docs.gitlab.com/api/rest/">REST API</a>,
 * section "Offset-based pagination"). A page number below one, or a page size below one, is refused with an
 * {@link IllegalArgumentException}.
 *
 * @param page the page to start at, counted from one
 * @param perPage the page size, or {@code null} to use the configured one; GitLab accepts at most 100
 */
public record GitLabOffsetRequest(int page, @Nullable Integer perPage) implements GitLabPageRequest {

    /** Name of the query parameter that holds the page number. */
    public static final String PAGE_PARAM = "page";

    public GitLabOffsetRequest {
        requireAtLeast(page, 1, PAGE_PARAM);
        if (perPage != null) {
            requireAtLeast(perPage, 1, "perPage");
        }
    }

    /**
     * Page {@code page} with the configured page size.
     *
     * @param page the page, counted from one
     * @return the request
     * @throws IllegalArgumentException if {@code page} is below one
     */
    public static GitLabOffsetRequest of(final int page) {
        return new GitLabOffsetRequest(page, null);
    }

    /**
     * Page {@code page} with pages of {@code perPage} items.
     *
     * @param page the page, counted from one
     * @param perPage the page size; GitLab accepts at most 100
     * @return the request
     * @throws IllegalArgumentException if {@code page} or {@code perPage} is below one
     */
    public static GitLabOffsetRequest of(final int page, final int perPage) {
        return new GitLabOffsetRequest(page, perPage);
    }

    @Override
    public OptionalInt explicitPerPage() {
        return perPage == null ? OptionalInt.empty() : OptionalInt.of(perPage);
    }

    @Override
    public void putQueryParameters(final Map<String, String> query) {
        query.put(PAGE_PARAM, String.valueOf(page));
    }
}
