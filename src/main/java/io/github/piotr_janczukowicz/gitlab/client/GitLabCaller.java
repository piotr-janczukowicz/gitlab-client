package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNotBlank;

import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabTierException;
import io.github.piotr_janczukowicz.gitlab.client.pagination.GitLabListSpec;
import io.github.piotr_janczukowicz.gitlab.client.pagination.GitLabPageTarget;
import io.github.piotr_janczukowicz.gitlab.client.pagination.GitLabPagers;
import io.github.piotr_janczukowicz.gitlab.json.GitLabJsonCodecs;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPage;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.http.HttpMethod;
import io.github.piotr_janczukowicz.http.client.AbstractHttpCaller;
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.json.JsonCodec;
import java.io.IOException;
import java.util.Optional;

/**
 * Sends the calls of the generated GitLab APIs through a {@link GitLabClient} and reads the JSON of the answers with
 * {@link GitLabJsonCodecs#defaults()}. It has the methods of {@link AbstractHttpCaller}: {@code send}, {@code fetch},
 * {@code fetchOptional} and the rest.
 */
public final class GitLabCaller extends AbstractHttpCaller {

    private final GitLabClient client;
    private final JsonCodec jsonCodec;

    GitLabCaller(final GitLabClient client, final JsonCodec jsonCodec) {
        super(client, jsonCodec);
        this.client = client;
        this.jsonCodec = jsonCodec;
    }

    /**
     * Requests the first page of a list and returns it with the way to the others. Every page is a plain {@code GET} of
     * the path, query and headers of {@code call}, so a call that asks for anything else is refused instead of being
     * sent as a different request.
     *
     * @param call the {@code GET} of the list, without a body; its query and headers apply to every page
     * @param request the page to start at
     * @param spec what the list accepts and returns
     * @return the first page
     * @param <T> type of an item
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code call} is not a {@code GET} or has a body, the list does not accept
     *     this kind of request, or the request names a page size above 100
     * @throws IOException if the request fails
     */
    public <T> GitLabPage<T> list(final HttpCall call, final GitLabPageRequest request, final GitLabListSpec<T> spec)
            throws IOException {
        requireNonNull(request, "request");
        requireNonNull(spec, "spec");
        return GitLabPagers.start(client, jsonCodec, client.pagination(), targetOf(call), request, spec);
    }

    /**
     * Refuses an operation that needs a higher subscription tier than the client was configured with. A generated
     * operation that carries {@link RequiresTier} calls it first, so a refused call sends nothing. The check applies
     * only to a self-managed or Dedicated instance, whose tier is the same for every group: a gitlab.com subscription
     * belongs to a top-level group, so no tier can be configured for it. With no tier configured nothing is refused.
     *
     * @param required the lowest tier that offers the operation
     * @param operation the operation, such as its {@code operationId}, named in the exception
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code operation} is blank
     * @throws GitLabTierException if the configured tier is lower than {@code required}
     */
    public void requireTier(final GitLabTier required, final String operation) {
        requireNonNull(required, "required");
        requireNotBlank(operation, "operation");
        final Optional<GitLabTier> configured = client.tier();
        if (configured.isPresent()
                && client.flavor() != GitLabFlavor.GITLAB_COM
                && !configured.get().includes(required)) {
            throw new GitLabTierException(operation, required, configured.get());
        }
    }

    /**
     * A caller over {@code client} with the GitLab JSON codec.
     *
     * @param client the client that sends the calls
     * @return the caller; it only holds the client and the codec, so create as many as needed
     * @throws NullPointerException if {@code client} is {@code null}
     */
    public static GitLabCaller of(final GitLabClient client) {
        return new GitLabCaller(requireNonNull(client, "client"), GitLabJsonCodecs.defaults());
    }

    private static GitLabPageTarget targetOf(final HttpCall call) {
        final String path = call.requirePath();
        if (call.method() != HttpMethod.GET) {
            throw new IllegalArgumentException("a list call must be a GET, not " + call.method());
        }
        if (!call.body().isAbsent()) {
            throw new IllegalArgumentException("a list call must not have a body");
        }
        return new GitLabPageTarget(path, call.query(), call.headers());
    }
}
