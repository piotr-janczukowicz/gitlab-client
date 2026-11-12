package io.github.piotr_janczukowicz.gitlab;

/** Names of the HTTP headers that the GitLab client packages read or send. */
public final class GitLabHeaders {

    /**
     * Makes an administrator's request act as another user, by user ID or username (<a
     * href="https://docs.gitlab.com/api/rest/authentication/">authentication</a>, section "Sudo"). The response depends
     * on it, so the client's response cache is keyed by it.
     */
    public static final String SUDO = "Sudo";

    /** The requests the client may send in the window ({@code RateLimit-Limit}). */
    public static final String RATE_LIMIT_LIMIT = "RateLimit-Limit";

    /** The requests left in the window ({@code RateLimit-Remaining}). */
    public static final String RATE_LIMIT_REMAINING = "RateLimit-Remaining";

    /** When the quota is restored, as a Unix time in seconds ({@code RateLimit-Reset}). */
    public static final String RATE_LIMIT_RESET = "RateLimit-Reset";

    /** The name of the throttle that the figures belong to ({@code RateLimit-Name}). */
    public static final String RATE_LIMIT_NAME = "RateLimit-Name";

    /** The page after the current one, counted from one ({@code X-Next-Page}); GitLab may leave it out. */
    public static final String NEXT_PAGE = "X-Next-Page";

    /** The total number of items of a list ({@code X-Total}); GitLab leaves it out above 10,000 records. */
    public static final String TOTAL = "X-Total";

    private GitLabHeaders() {
        throw new UnsupportedOperationException("Utility class");
    }
}
