package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProvider;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import java.util.function.Supplier;

/**
 * Credentials for GitLab's token types (<a href="https://docs.gitlab.com/api/rest/authentication/">REST API
 * authentication</a>). Personal, project and group access tokens and OAuth 2.0 tokens are sent as {@code Authorization:
 * Bearer}, which GitLab documents for all of them. A job token is sent as the {@code JOB-TOKEN} header: GitLab
 * documents no other header for it, and it reaches only the endpoints that the project allows. None of them is ever put
 * in the query string, and none shows its token in {@code toString}.
 *
 * <p>Deploy tokens cannot be used with the GitLab API, and a token sent two ways is refused by GitLab with a 400, so
 * there is no factory for either.
 */
public final class GitLabAuthenticationProviders {

    private GitLabAuthenticationProviders() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * A personal, project or group access token.
     *
     * @param token the token
     * @return provider that always sends {@code Authorization: Bearer <token>}
     * @throws NullPointerException if {@code token} is {@code null}
     * @throws IllegalArgumentException if {@code token} is blank
     */
    public static AuthenticationProvider personalToken(final String token) {
        return AuthenticationProviders.bearer(token);
    }

    /**
     * An OAuth 2.0 access token. OAuth access tokens last two hours, so use {@link #rotatingToken(String, Supplier)}
     * for a client that outlives one.
     *
     * @param token the token
     * @return provider that always sends {@code Authorization: Bearer <token>}
     * @throws NullPointerException if {@code token} is {@code null}
     * @throws IllegalArgumentException if {@code token} is blank
     */
    public static AuthenticationProvider oauth(final String token) {
        return AuthenticationProviders.bearer(token);
    }

    /**
     * The token of a CI/CD job, {@code CI_JOB_TOKEN}.
     *
     * @param token the token
     * @return provider that always sends the {@code JOB-TOKEN} header and no {@code Authorization} header
     * @throws NullPointerException if {@code token} is {@code null}
     * @throws IllegalArgumentException if {@code token} is blank
     */
    public static AuthenticationProvider jobToken(final String token) {
        return AuthenticationProviders.header("JOB-TOKEN", token);
    }

    /**
     * A bearer token that the caller replaces over time, such as a refreshed OAuth token or a rotated access token. The
     * identity names the principal, so the rate-limit state and the response cache survive each replacement; it must be
     * unique per principal and is never a secret. A 401 is not retried.
     *
     * @param identity name of the principal, such as a service account; it appears in logs, so it must not be the token
     * @param tokens supplies the current token on each request
     * @return provider that sends {@code Authorization: Bearer} with the token {@code tokens} returns
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code identity} is blank, or at request time if {@code tokens} returns a
     *     blank value
     */
    public static AuthenticationProvider rotatingToken(final String identity, final Supplier<String> tokens) {
        return new RotatingTokenAuthenticationProvider(identity, requireNonNull(tokens, "tokens"));
    }
}
