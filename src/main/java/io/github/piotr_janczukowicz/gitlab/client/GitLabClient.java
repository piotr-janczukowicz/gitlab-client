package io.github.piotr_janczukowicz.gitlab.client;

import io.github.piotr_janczukowicz.gitlab.client.internal.DefaultGitLabClient;
import io.github.piotr_janczukowicz.gitlab.client.ratelimit.GitLabRateLimitSnapshot;
import io.github.piotr_janczukowicz.http.client.HttpClientExecutor;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import java.util.Optional;

/**
 * Sends requests to the REST API of one GitLab instance, with the credentials it was created with. Create one with
 * {@link GitLabClients}, once per credential, and share it: it owns the transport, the retry state and the response
 * cache. Close it when the application is done with it.
 *
 * <p>Request paths are relative to {@code /api/v4} of the instance, so {@code /projects/1} reaches
 * {@code https://gitlab.com/api/v4/projects/1}.
 */
public sealed interface GitLabClient extends HttpClientExecutor, AutoCloseable permits DefaultGitLabClient {

    /**
     * The GitLab offering this client talks to: the flavor it was configured with, or the one its base URI implies.
     *
     * @return the flavor
     */
    GitLabFlavor flavor();

    /**
     * The subscription tier of the instance, as configured.
     *
     * @return the tier, or empty if it was not configured
     */
    Optional<GitLabTier> tier();

    /**
     * The quota that the latest response reported. GitLab sends it with every response except some of the Projects,
     * Groups and Users APIs, and shows only the most restrictive throttle, so quota left here does not promise that a
     * request is accepted: application limits such as issue creation are not in it.
     *
     * @return the quota of the latest response that carried one, or empty if none did yet
     */
    Optional<GitLabRateLimitSnapshot> lastRateLimit();

    /**
     * Page size and page limit of list calls.
     *
     * @return the pagination settings
     */
    PaginationConfiguration pagination();

    /** Releases the transport. A closed client cannot be used again. */
    @Override
    void close();
}
