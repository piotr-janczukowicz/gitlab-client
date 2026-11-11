package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.client.pagination.GitLabListSpec;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPage;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.http.client.HttpResourceApi;
import io.github.piotr_janczukowicz.json.JsonType;
import io.github.piotr_janczukowicz.uri.PathTemplate;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Base of the generated {@code GitLab*Api} classes, one per resource. A subclass builds a call from a path template
 * with {@code get}, {@code post}, {@code put}, {@code patch} and {@code delete}, which encode each value as one path
 * segment, so a project path such as {@code group/project} becomes {@code group%2Fproject}, and sends it through
 * {@link #caller}.
 */
public abstract class GitLabResourceApi extends HttpResourceApi {

    /** Sends the calls of this API and reads the answers. */
    protected final GitLabCaller caller;

    /**
     * Creates the API over {@code caller}.
     *
     * @param caller the caller that sends this API's calls
     * @throws NullPointerException if {@code caller} is {@code null}
     */
    protected GitLabResourceApi(final GitLabCaller caller) {
        super();
        this.caller = requireNonNull(caller, "caller");
    }

    /**
     * Requests the first page of a list that GitLab pages by number.
     *
     * @param path template of the path of the list
     * @param query the query parameters of every page
     * @param request the page to start at
     * @param type the type of the JSON array of items that each page returns
     * @param values the values of the path template, each encoded as one path segment
     * @return the first page; the others are requested when they are asked for
     * @param <T> type of an item
     * @throws NullPointerException if an argument is {@code null}
     * @throws IOException if the request fails
     */
    protected final <T> GitLabPage<T> pageList(
            final PathTemplate path,
            final Map<String, String> query,
            final GitLabPageRequest request,
            final JsonType<List<T>> type,
            final Object... values)
            throws IOException {
        requireNonNull(path, "path");
        requireNonNull(request, "request");
        requireNonNull(type, "type");
        return caller.list(get(path, values).withQuery(query), request, GitLabListSpec.offset(type));
    }
}
