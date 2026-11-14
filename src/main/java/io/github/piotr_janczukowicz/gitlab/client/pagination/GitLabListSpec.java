package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequestKind;
import io.github.piotr_janczukowicz.json.JsonType;
import io.github.piotr_janczukowicz.util.SetUtils;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What one list endpoint accepts and returns: the kinds of page request it supports, and the type of the JSON array
 * that it returns. A request of another kind is refused before anything is sent, because GitLab supports some kinds
 * only on selected endpoints.
 *
 * <p>A {@code null} argument is refused with a {@link NullPointerException}.
 *
 * @param allowedKinds the kinds of request the endpoint accepts; a copy that cannot be changed
 * @param type the type of the JSON array of items that each page returns
 * @param <T> type of an item
 */
public record GitLabListSpec<T>(Set<GitLabPageRequestKind> allowedKinds, JsonType<List<T>> type) {

    public GitLabListSpec {
        allowedKinds = SetUtils.copyOf(allowedKinds, "allowedKinds");
        requireNonNull(type, "type");
    }

    /**
     * A list that GitLab pages by number: it accepts a request for a page and a request without parameters.
     *
     * @param type the type of the JSON array of items
     * @return the spec
     * @param <T> type of an item
     */
    public static <T> GitLabListSpec<T> offset(final JsonType<List<T>> type) {
        return new GitLabListSpec<>(Set.of(GitLabPageRequestKind.NONE, GitLabPageRequestKind.OFFSET), type);
    }

    /**
     * A list that GitLab pages by number or by keyset: it accepts a request for a page, a keyset request and a request
     * without parameters. GitLab supports keyset only on selected endpoints and orderings, so a list offers it only
     * where its documentation says so.
     *
     * @param type the type of the JSON array of items
     * @return the spec
     * @param <T> type of an item
     */
    public static <T> GitLabListSpec<T> offsetOrKeyset(final JsonType<List<T>> type) {
        return new GitLabListSpec<>(
                Set.of(GitLabPageRequestKind.NONE, GitLabPageRequestKind.OFFSET, GitLabPageRequestKind.KEYSET), type);
    }

    /**
     * Checks that the endpoint accepts a request.
     *
     * @param request the request
     * @throws NullPointerException if {@code request} is {@code null}
     * @throws IllegalArgumentException if the endpoint does not accept this kind of request
     */
    public void requireAllowed(final GitLabPageRequest request) {
        final GitLabPageRequestKind kind = GitLabPageRequestKind.of(requireNonNull(request, "request"));
        if (!allowedKinds.contains(kind)) {
            throw new IllegalArgumentException(
                    "this list does not accept a " + kind + " page request; it accepts " + new TreeSet<>(allowedKinds));
        }
    }
}
