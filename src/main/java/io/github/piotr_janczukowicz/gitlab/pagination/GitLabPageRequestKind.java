package io.github.piotr_janczukowicz.gitlab.pagination;

/** The kinds of {@link GitLabPageRequest}, so that a list can say which of them it accepts. */
public enum GitLabPageRequestKind {

    /** A request that adds no pagination parameter: the server's defaults, with the next pages followed. */
    NONE,

    /** A request for a page by number, GitLab's offset pagination. */
    OFFSET,

    /** A request for a list by keyset, supported only on selected endpoints and orderings. */
    KEYSET;

    /**
     * The kind of a request.
     *
     * @param request the request
     * @return its kind
     * @throws NullPointerException if {@code request} is {@code null}
     */
    public static GitLabPageRequestKind of(final GitLabPageRequest request) {
        return switch (request) {
            case GitLabNoneRequest ignored -> NONE;
            case GitLabOffsetRequest ignored -> OFFSET;
            case GitLabKeysetRequest ignored -> KEYSET;
        };
    }
}
