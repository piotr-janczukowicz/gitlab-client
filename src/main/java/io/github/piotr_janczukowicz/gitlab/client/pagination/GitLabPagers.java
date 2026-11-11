package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.gitlab.pagination.GitLabKeysetRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabOffsetRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPage;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageRequest;
import io.github.piotr_janczukowicz.gitlab.pagination.GitLabPageWalk;
import io.github.piotr_janczukowicz.http.HttpHeaderNames;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.http.client.HttpCall;
import io.github.piotr_janczukowicz.http.client.HttpClientExecutor;
import io.github.piotr_janczukowicz.http.client.HttpOriginViolationException;
import io.github.piotr_janczukowicz.http.uri.HttpUris;
import io.github.piotr_janczukowicz.json.JsonCodec;
import io.github.piotr_janczukowicz.json.JsonType;
import io.github.piotr_janczukowicz.pagination.LinkHeader;
import io.github.piotr_janczukowicz.pagination.PageWalkGuard;
import io.github.piotr_janczukowicz.pagination.PaginationConfiguration;
import io.github.piotr_janczukowicz.pagination.PaginationException;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import okhttp3.HttpUrl;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts the walk over a list that GitLab pages by number or by keyset, and follows it page by page.
 *
 * <p>The walk follows the {@code Link} header with {@code rel="next"} of each response and never builds a URL of its
 * own while that header is there, as GitLab asks (<a href="https://docs.gitlab.com/api/rest/">REST API</a>, section
 * "Pagination Link header"). A response with a {@code Link} header and no {@code rel="next"} is the last page, even if
 * {@code X-Next-Page} says otherwise. Only a response with no {@code Link} header at all falls back to
 * {@code X-Next-Page}, which names the page number to request: GitLab.com may leave pagination headers out. A response
 * with neither is the last page. A keyset walk never falls back: a response with no {@code Link} header is its last
 * page, because the cursor of the next page exists only in the link.
 *
 * <p>Every page is requested with the headers of the first one, so a list read with {@code Sudo} is read as that user
 * throughout. A link to another origin is refused, and so is one that was already followed or one past the limit of
 * pages.
 */
public final class GitLabPagers {

    private static final Logger LOG = LoggerFactory.getLogger(GitLabPagers.class);

    private static final String PAGE_PARAM = GitLabOffsetRequest.PAGE_PARAM;
    private static final byte ARRAY_START = '[';
    private static final String STALE_MESSAGE =
            "this page is not the latest of its walk: a walk goes forward only, so advance from the page that "
                    + "next() returned last";
    private static final String HOST_HINT = "; the Link scheme, host or port differs from the base URI: check the"
            + " external_url of the GitLab instance, which a proxy may not match";

    private GitLabPagers() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Requests the first page of a list.
     *
     * @param executor sends the requests, with the credentials of the client
     * @param json reads the JSON array of each page
     * @param pagination the page size and the limit of pages
     * @param target the path, query and headers of the list
     * @param request the page to start at
     * @param spec what the list accepts and returns
     * @return the first page; the others are requested when they are asked for
     * @param <T> type of an item
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if the list does not accept this kind of request, or the request names a page
     *     size above 100, or the query already holds a parameter that the request sets
     * @throws IOException if the request fails
     */
    public static <T> GitLabPage<T> start(
            final HttpClientExecutor executor,
            final JsonCodec json,
            final PaginationConfiguration pagination,
            final GitLabPageTarget target,
            final GitLabPageRequest request,
            final GitLabListSpec<T> spec)
            throws IOException {
        requireNonNull(executor, "executor");
        requireNonNull(json, "json");
        requireNonNull(pagination, "pagination");
        requireNonNull(target, "target");
        requireNonNull(request, "request");
        requireNonNull(spec, "spec");
        spec.requireAllowed(request);
        return new Session<>(executor, json, pagination, target, request, spec.type()).fetchFirst();
    }

    private static final class Session<T> {

        private final HttpClientExecutor http;
        private final JsonCodec json;
        private final JsonType<List<T>> type;
        private final GitLabPageTarget target;
        private final Map<String, List<String>> firstQuery;
        private final PageWalkGuard guard;
        private final URI firstUri;
        private URI currentUri;
        private int latestPageCount;
        private @Nullable String linkHeader;
        private final boolean isOffsetFallbackAllowed;
        private @Nullable Integer nextPage;

        private Session(
                final HttpClientExecutor executor,
                final JsonCodec json,
                final PaginationConfiguration pagination,
                final GitLabPageTarget target,
                final GitLabPageRequest request,
                final JsonType<List<T>> type) {
            this.http = executor;
            this.json = json;
            this.type = type;
            this.isOffsetFallbackAllowed = !(request instanceof GitLabKeysetRequest);
            this.target = target;
            final int perPage = GitLabPageQueries.boundPerPage(request, pagination);
            this.firstQuery = GitLabPageQueries.queryValues(request, target.query(), perPage);
            this.firstUri = HttpUris.resolveApiPath(http.baseUri(), target.path(), firstQuery);
            this.currentUri = firstUri;
            this.guard = new PageWalkGuard(pagination.maxPages(), firstUri);
        }

        private GitLabPage<T> fetchFirst() throws IOException {
            return toPage(
                    http.execute(HttpCall.get(target.path())
                            .withQueryValues(firstQuery)
                            .withHeaders(target.overlayHeaders())),
                    1);
        }

        private Optional<GitLabPage<T>> advanceFrom(final GitLabPage<T> current) throws IOException {
            final Optional<URI> link = nextLink();
            if (link.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(loadPage(link.get(), current.pageCount()));
        }

        private Optional<URI> nextLink() {
            if (linkHeader != null) {
                final Optional<URI> next = LinkHeader.parse(linkHeader).next().map(this::absoluteLink);
                if (next.isEmpty()) {
                    warnIfNextPageIsNamed();
                    logEnd("the Link header has no rel=\"next\"");
                }
                return next;
            }
            if (nextPage == null) {
                logEnd("there is no Link header and X-Next-Page names no page");
                return Optional.empty();
            }
            return Optional.of(withPage(nextPage));
        }

        private URI withPage(final int page) {
            final HttpUrl current = HttpUrl.get(currentUri);
            return requireNonNull(current, "currentUri")
                    .newBuilder()
                    .setQueryParameter(PAGE_PARAM, String.valueOf(page))
                    .build()
                    .uri();
        }

        private GitLabPage<T> loadPage(final URI link, final int pageCount) throws IOException {
            guard.requireNext(link, pageCount);
            LOG.atDebug()
                    .setMessage("Loading page {} of {}")
                    .addArgument(pageCount + 1)
                    .addArgument(() -> HttpUris.describeSafe(link))
                    .log();
            final GitLabPage<T> page = toPage(execute(link), pageCount + 1);
            guard.markFollowed(link);
            currentUri = link;
            return page;
        }

        private HttpResponse execute(final URI link) throws IOException {
            try {
                return http.execute(HttpCall.get(link).withHeaders(target.overlayHeaders()));
            } catch (final HttpOriginViolationException ex) {
                throw new PaginationException(outsideBaseMessage(link), ex);
            }
        }

        private GitLabPage<T> toPage(final HttpResponse response, final int pageCount) {
            final byte[] body = response.mapBody(bytes -> bytes, () -> new byte[0]);
            requireJsonArray(body);
            final List<T> items = json.decode(body, type);
            linkHeader = response.header(HttpHeaderNames.LINK).orElse(null);
            nextPage = fallbackPage(response);
            latestPageCount = pageCount;
            return new GitLabPage<>(items, new Walk<>(this), pageCount, GitLabPageHeaders.total(response));
        }

        // A keyset cursor exists only in the Link header, so a keyset walk has no page number to fall back to.
        private @Nullable Integer fallbackPage(final HttpResponse response) {
            if (!isOffsetFallbackAllowed) {
                return null;
            }
            return GitLabPageHeaders.nextPage(response);
        }

        // The mapper accepts a single value where an array is expected, so an endpoint that answers with an object
        // would be read as a list of one defaulted item. A list response must be an array, and the body is never
        // quoted.
        private static void requireJsonArray(final byte[] body) {
            int index = 0;
            while (index < body.length && isWhitespace(body[index])) {
                index++;
            }
            if (index == body.length || body[index] != ARRAY_START) {
                throw new PaginationException("the body of a list response must be a JSON array, but it is not");
            }
        }

        private static boolean isWhitespace(final byte value) {
            return value == ' ' || value == '\t' || value == '\n' || value == '\r';
        }

        private URI absoluteLink(final URI link) {
            if (link.isAbsolute()) {
                return link;
            }
            try {
                return HttpUris.resolveReference(currentUri, link.toString());
            } catch (final IllegalArgumentException ex) {
                throw new PaginationException(
                        "Link rel=\"next\" cannot be resolved against the request URI: " + HttpUris.describeSafe(link),
                        ex);
            }
        }

        private String outsideBaseMessage(final URI link) {
            final String message = "Link rel=\"next\" is outside the API base URI: " + HttpUris.describeSafe(link);
            return differsOnlyByOrigin(link) ? message + HOST_HINT : message;
        }

        private boolean differsOnlyByOrigin(final URI link) {
            final URI base = http.baseUri();
            return !originOf(base).equals(originOf(link)) && link.getPath().startsWith(base.getPath());
        }

        private static String originOf(final URI uri) {
            final String scheme = String.valueOf(uri.getScheme()).toLowerCase(Locale.ROOT);
            final int defaultPort = "https".equals(scheme) ? 443 : 80;
            final int port = uri.getPort() == -1 ? defaultPort : uri.getPort();
            return scheme + "://" + String.valueOf(uri.getHost()).toLowerCase(Locale.ROOT) + ":" + port;
        }

        private void warnIfNextPageIsNamed() {
            if (nextPage != null) {
                LOG.atWarn()
                        .setMessage("The Link header of page {} has no rel=\"next\" but X-Next-Page names page {}: the"
                                + " list ends here, so check whether a proxy rewrote the Link header")
                        .addArgument(latestPageCount)
                        .addArgument(nextPage)
                        .log();
            }
        }

        private void logEnd(final String reason) {
            LOG.atDebug()
                    .setMessage("No page after page {} of {}: {}")
                    .addArgument(latestPageCount)
                    .addArgument(() -> HttpUris.describeSafe(firstUri))
                    .addArgument(reason)
                    .log();
        }

        private void requireLatest(final int pageCount) {
            if (pageCount != latestPageCount) {
                throw new IllegalStateException(STALE_MESSAGE);
            }
        }
    }

    private record Walk<T>(Session<T> session) implements GitLabPageWalk<T> {

        @Override
        public Optional<GitLabPage<T>> loadNext(final GitLabPage<T> current) throws IOException {
            return session.advanceFrom(current);
        }

        @Override
        public void requireLatest(final GitLabPage<T> page) {
            session.requireLatest(page.pageCount());
        }
    }
}
