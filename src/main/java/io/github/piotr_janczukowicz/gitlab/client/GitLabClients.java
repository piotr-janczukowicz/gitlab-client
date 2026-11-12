package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.client.internal.ClientStacks;
import io.github.piotr_janczukowicz.http.HttpUserAgents;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProvider;
import io.github.piotr_janczukowicz.http.transport.HttpTransport;
import io.github.piotr_janczukowicz.http.transport.HttpTransports;
import io.github.piotr_janczukowicz.http.transport.RetryingHttpTransport;

/**
 * Creates {@link GitLabClient}s with the full request stack: a limit on concurrent requests, retry with rate-limit
 * waits, optional pacing of requests that change state, a conditional response cache and an OkHttp transport.
 */
public final class GitLabClients {

    private static final String CONFIGURATION_PARAMETER = "configuration";

    private GitLabClients() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * A client for gitlab.com with every default.
     *
     * @param auth source of the credentials, such as {@code AuthenticationProviders.bearer(token)}
     * @param userAgent the {@code User-Agent} of every request, such as {@code my-app/1.0}
     * @return the client; close it when the application is done with it
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code userAgent} is blank or not visible ASCII
     */
    public static GitLabClient create(final AuthenticationProvider auth, final String userAgent) {
        return create(auth, userAgent, GitLabClientConfiguration.defaults());
    }

    /**
     * A client for the instance that {@code configuration} names.
     *
     * @param auth source of the credentials, such as {@code AuthenticationProviders.bearer(token)}
     * @param userAgent the {@code User-Agent} of every request, such as {@code my-app/1.0}
     * @param configuration the settings
     * @return the client; close it when the application is done with it
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code userAgent} is blank or not visible ASCII, or the default headers of
     *     {@code configuration} set {@code User-Agent}
     * @throws IllegalStateException if the base URI is plain {@code http} to a remote host and the configuration does
     *     not allow that
     */
    public static GitLabClient create(
            final AuthenticationProvider auth, final String userAgent, final GitLabClientConfiguration configuration) {
        requireNonNull(auth, "auth");
        HttpUserAgents.requireValid(userAgent);
        requireNonNull(configuration, CONFIGURATION_PARAMETER);
        final HttpTransport okHttp = HttpTransports.okHttp(configuration.transport());
        return ClientStacks.assemble(okHttp, auth, userAgent, configuration, RetryingHttpTransport::new)
                .build();
    }
}
