package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a list request asks for besides its page: the path, the query that every page shares, and the headers that every
 * page is sent with, such as {@code Sudo}, so that a list read as one user is read as that user throughout.
 *
 * <p>A {@code null} argument is refused with a {@link NullPointerException}.
 *
 * @param path the path of the list, relative to the API root, with its values encoded
 * @param query the query parameters of every page, in the order they are sent; a copy that cannot be changed
 * @param overlayHeaders the headers sent with every page
 */
public record GitLabPageTarget(String path, Map<String, List<String>> query, HttpHeaders overlayHeaders) {

    public GitLabPageTarget {
        requireNonNull(path, "path");
        query = copyValues(requireNonNull(query, "query"));
        requireNonNull(overlayHeaders, "overlayHeaders");
    }

    private static Map<String, List<String>> copyValues(final Map<String, List<String>> query) {
        final Map<String, List<String>> copy = new LinkedHashMap<>();
        query.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(copy);
    }
}
