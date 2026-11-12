package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import java.net.URI;

/**
 * The GitLab offering a client talks to. It sets defaults only: no operation is guarded by it, because the OpenAPI
 * description does not mark operations that only one offering has.
 */
public enum GitLabFlavor {

    /** GitLab.com, the hosted service at {@code gitlab.com}. */
    GITLAB_COM,

    /** GitLab Dedicated, a single-tenant instance that GitLab runs for one customer. */
    DEDICATED,

    /** GitLab Self-Managed, an instance that the customer runs. */
    SELF_MANAGED;

    private static final String GITLAB_COM_HOST = "gitlab.com";

    /**
     * The flavor that a base URI implies. Only the host {@code gitlab.com}, compared without regard to case, is
     * {@link #GITLAB_COM}; any other host is {@link #SELF_MANAGED}. {@link #DEDICATED} is never inferred, because
     * GitLab documents no host name pattern for it and Dedicated instances can use a custom domain (<a
     * href="https://docs.gitlab.com/administration/dedicated/">GitLab Dedicated</a>): set it on the configuration.
     *
     * @param instanceRoot root URI of the instance, such as {@code https://gitlab.com}
     * @return the implied flavor
     * @throws NullPointerException if {@code instanceRoot} is {@code null}
     */
    public static GitLabFlavor of(final URI instanceRoot) {
        requireNonNull(instanceRoot, "instanceRoot");
        return GITLAB_COM_HOST.equalsIgnoreCase(instanceRoot.getHost()) ? GITLAB_COM : SELF_MANAGED;
    }
}
