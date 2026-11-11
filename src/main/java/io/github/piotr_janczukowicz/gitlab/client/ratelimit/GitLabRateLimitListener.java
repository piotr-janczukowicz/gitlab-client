package io.github.piotr_janczukowicz.gitlab.client.ratelimit;

/**
 * Receives the rate-limit quota that GitLab reports. GitLab sends it with every response, so the listener is called for
 * each response that carries it, on the thread that received the response. It should return quickly. A failure it
 * throws is logged and does not affect the call.
 */
@FunctionalInterface
public interface GitLabRateLimitListener {

    /**
     * Called with the quota of a response.
     *
     * @param snapshot the quota that the response reported
     */
    void onRateLimit(GitLabRateLimitSnapshot snapshot);
}
