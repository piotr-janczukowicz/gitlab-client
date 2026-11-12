package io.github.piotr_janczukowicz.gitlab.client.internal;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.http.uri.HttpUris;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Where the REST API of a GitLab instance lives: the instance root plus {@code api/v4}. The root keeps its sub-path, so
 * an instance served under a relative URL root such as {@code https://example.com/gitlab} has its API at
 * {@code https://example.com/gitlab/api/v4/}.
 *
 * <p>The API path {@code /api/v4} is documented (<a href="https://docs.gitlab.com/api/rest/">REST API</a>, section
 * "Make a REST API request"). GitLab documents the relative URL root of an installation, but nowhere the API URL under
 * it, so joining the two is not a documented form. It is how the other clients do it: python-gitlab, GitLab's official
 * Go client and gitlab4j-api each keep the sub-path of the URL they are given and append {@code /api/v4} to it. It has
 * not been tried against a live instance that runs under a relative URL root. Of those clients, only the Go client also
 * accepts a root that already ends in {@code /api/v4} without repeating it, as this class does.
 *
 * <p>The constructor refuses a root that is not an absolute {@code http} or {@code https} URI with a host that Java can
 * parse (a name with an underscore is not one), or that carries user info, a query or a fragment, with an
 * {@link IllegalArgumentException} that names the URI without them; {@code null} is a {@link NullPointerException}.
 *
 * @param instanceRoot root URI of the instance; a root that already ends in {@code /api/v4} is accepted and not
 *     extended again
 */
public record GitLabApiBase(URI instanceRoot) {

    private static final Set<String> SCHEMES = Set.of("http", "https");
    private static final String API_ROOT = "/api/v4";

    public GitLabApiBase {
        requireNonNull(instanceRoot, "instanceRoot");
        final String scheme =
                instanceRoot.getScheme() == null ? "" : instanceRoot.getScheme().toLowerCase(Locale.ROOT);
        if (!SCHEMES.contains(scheme) || instanceRoot.getRawAuthority() == null || instanceRoot.getHost() == null) {
            throw new IllegalArgumentException("instanceRoot must be an absolute http or https URI with a host: "
                    + HttpUris.describeSafe(instanceRoot));
        }
        if (instanceRoot.getRawUserInfo() != null
                || instanceRoot.getRawQuery() != null
                || instanceRoot.getRawFragment() != null) {
            throw new IllegalArgumentException("instanceRoot must not carry user info, a query or a fragment: "
                    + HttpUris.describeSafe(instanceRoot));
        }
    }

    /**
     * Creates the base of an instance.
     *
     * @param instanceRoot root URI of the instance
     * @return the base
     * @throws NullPointerException if {@code instanceRoot} is {@code null}
     * @throws IllegalArgumentException if {@code instanceRoot} is not an acceptable root, see
     *     {@link #GitLabApiBase(URI)}
     */
    public static GitLabApiBase of(final URI instanceRoot) {
        return new GitLabApiBase(instanceRoot);
    }

    /**
     * The URI that request paths are resolved against.
     *
     * @return the instance root, its sub-path kept, followed by {@code /api/v4/}; the same URI whether or not the root
     *     already ended in {@code /api/v4}
     */
    public URI apiUri() {
        final String path = withoutTrailingSlashes(instanceRoot.getRawPath());
        final String apiPath = path.endsWith(API_ROOT) ? path : path + API_ROOT;
        return URI.create(instanceRoot.getScheme() + "://" + instanceRoot.getRawAuthority() + apiPath + "/");
    }

    private static String withoutTrailingSlashes(final String path) {
        int end = path.length();
        while (end > 0 && path.charAt(end - 1) == '/') {
            end--;
        }
        return path.substring(0, end);
    }
}
