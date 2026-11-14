package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.pagination.GitLabNoneRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.pagination.PageQueries;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** Builds the query of the first page of a list. */
final class GitLabPageQueries {

    private static final String PER_PAGE = "per_page";

    private GitLabPageQueries() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The page size to send: the one the request names, or the configured one, never more than GitLab's maximum.
     *
     * @throws IllegalArgumentException if the request names a size above the maximum of 100
     */
    static int boundPerPage(final GitLabPageRequest request, final PaginationConfiguration configuration) {
        requireNonNull(request, "request");
        requireNonNull(configuration, "configuration");
        final OptionalInt explicit = request.explicitPerPage();
        return PageQueries.boundPerPage(
                explicit.isPresent() ? explicit.getAsInt() : null, configuration, PaginationConfiguration.MAX_PER_PAGE);
    }

    /**
     * The base query, then the page parameters of the request, then the page size unless the request adds none.
     *
     * @throws IllegalArgumentException if the base query already holds a parameter that the request adds, because the
     *     two would disagree and one of them would be dropped silently
     */
    static Map<String, List<String>> queryValues(
            final GitLabPageRequest request, final Map<String, List<String>> baseQuery, final int perPage) {
        requireNonNull(request, "request");
        requireNonNull(baseQuery, "baseQuery");
        final Map<String, String> parameters = new LinkedHashMap<>();
        request.putQueryParameters(parameters);
        if (!(request instanceof GitLabNoneRequest)) {
            parameters.put(PER_PAGE, String.valueOf(perPage));
        }
        parameters.keySet().forEach(name -> requireAbsent(baseQuery, name));
        return PageQueries.withParameters(baseQuery, parameters);
    }

    private static void requireAbsent(final Map<String, List<String>> baseQuery, final String name) {
        if (baseQuery.containsKey(name)) {
            throw new IllegalArgumentException("the query must not hold " + name
                    + ": the page request sets it, so choose the page size and order on the request");
        }
    }
}
