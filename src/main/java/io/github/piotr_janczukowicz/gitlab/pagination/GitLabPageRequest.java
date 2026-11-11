package io.github.piotr_janczukowicz.gitlab.pagination;

import java.util.Map;
import java.util.OptionalInt;

/**
 * Which page of a list to start at, and how big the pages are. Create one with {@link #byPage()}, {@link #none()} or
 * the factories of {@link GitLabOffsetRequest} and {@link GitLabKeysetRequest}. The walk then follows the links that
 * GitLab sends, so a request only names the first page.
 */
public sealed interface GitLabPageRequest permits GitLabNoneRequest, GitLabOffsetRequest, GitLabKeysetRequest {

    /**
     * The page size that this request asks for.
     *
     * @return the size, or empty to use the configured page size
     */
    OptionalInt explicitPerPage();

    /**
     * Adds the parameters that select the first page.
     *
     * @param query receives the parameters; an existing entry of the same name is replaced
     */
    void putQueryParameters(Map<String, String> query);

    /**
     * The first page, with the configured page size.
     *
     * @return the request
     */
    static GitLabPageRequest byPage() {
        return GitLabOffsetRequest.of(1);
    }

    /**
     * No pagination parameter at all: the server's default page size and first page.
     *
     * @return the request
     */
    static GitLabPageRequest none() {
        return new GitLabNoneRequest();
    }
}
