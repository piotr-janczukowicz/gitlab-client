package io.github.piotr_janczukowicz.gitlab.pagination;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireAtLeastIfPresent;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNotBlank;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * A request for a list by keyset, which GitLab supports only on selected endpoints and orderings (<a
 * href="https://docs.gitlab.com/api/rest/">REST API</a>, section "Keyset-based pagination"). {@code order_by} and
 * {@code sort} are mandatory there, so a blank {@code orderBy}, or a {@code sort} other than {@code asc} or
 * {@code desc}, is refused with an {@link IllegalArgumentException}, and so is a page size below one. A {@code null}
 * {@code orderBy} or {@code sort} is refused with a {@link NullPointerException}. The walk then follows the link that
 * each response names, as GitLab asks, and never builds one.
 *
 * @param orderBy the column to order by; the values that an endpoint accepts are listed in its documentation
 * @param sort {@code asc} or {@code desc}
 * @param perPage the page size, or {@code null} to use the configured one; GitLab accepts at most 100
 */
public record GitLabKeysetRequest(
        String orderBy, String sort, @Nullable Integer perPage) implements GitLabPageRequest {

    private static final Set<String> SORTS = Set.of("asc", "desc");

    public GitLabKeysetRequest {
        requireNotBlank(orderBy, "orderBy");
        requireNotBlank(sort, "sort");
        if (!SORTS.contains(sort)) {
            throw new IllegalArgumentException("sort must be asc or desc");
        }
        requireAtLeastIfPresent(perPage, 1, "perPage");
    }

    /**
     * A keyset request with the configured page size.
     *
     * @param orderBy the column to order by
     * @param sort {@code asc} or {@code desc}
     * @return the request
     * @throws IllegalArgumentException if {@code orderBy} is blank or {@code sort} is not {@code asc} or {@code desc}
     */
    public static GitLabKeysetRequest of(final String orderBy, final String sort) {
        return new GitLabKeysetRequest(orderBy, sort, null);
    }

    /**
     * A keyset request with pages of {@code perPage} items.
     *
     * @param orderBy the column to order by
     * @param sort {@code asc} or {@code desc}
     * @param perPage the page size; GitLab accepts at most 100
     * @return the request
     * @throws IllegalArgumentException if {@code orderBy} is blank, {@code sort} is not {@code asc} or {@code desc} or
     *     {@code perPage} is below one
     */
    public static GitLabKeysetRequest of(final String orderBy, final String sort, final int perPage) {
        return new GitLabKeysetRequest(orderBy, sort, perPage);
    }

    @Override
    public OptionalInt explicitPerPage() {
        return perPage == null ? OptionalInt.empty() : OptionalInt.of(perPage);
    }

    @Override
    public void putQueryParameters(final Map<String, String> query) {
        query.put("pagination", "keyset");
        query.put("order_by", orderBy);
        query.put("sort", sort);
    }
}
