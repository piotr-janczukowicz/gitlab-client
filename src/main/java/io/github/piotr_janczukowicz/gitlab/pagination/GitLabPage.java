package io.github.piotr_janczukowicz.gitlab.pagination;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireAtLeast;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.pagination.Page;
import io.github.piotr_janczukowicz.pagination.Pages;
import io.github.piotr_janczukowicz.util.ListUtils;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * One page of a list that GitLab hands out in pages, and the way to the next one. The next page is requested when it is
 * asked for, never earlier, so {@code stream().limit(n)} stops further requests.
 *
 * <p>The walk follows the {@code Link} header of each response, as GitLab asks, and falls back to {@code X-Next-Page}
 * only when a response has no {@code Link} header at all. A response with neither ends the walk. The page limit of the
 * client configuration ends it with an exception instead of stopping silently.
 *
 * <p>Offset pagination has a limit on how deep it goes: 50,000 records by default, on endpoints that also offer keyset
 * pagination (<a href="https://docs.gitlab.com/administration/instance_limits/">instance limits</a>). A walk past it
 * fails partway with GitLab's own error, and keyset pagination is the way past it.
 *
 * <p>A page is not thread-safe to advance, and it can only advance while it is the latest page of its walk.
 *
 * @param <T> type of an item
 */
public final class GitLabPage<T> implements Page<T, GitLabPage<T>> {

    private final List<T> items;
    private final GitLabPageWalk<T> walk;
    private final int pageCount;
    private final @Nullable Integer totalCount;

    /**
     * Describes a page.
     *
     * @param items the items of this page; copied
     * @param walk how to reach the next page
     * @param pageCount the number of this page in its walk, counted from one
     * @param totalCount the total number of items that GitLab reports, or {@code null} if it does not
     * @throws NullPointerException if {@code items} or {@code walk} is {@code null}
     * @throws IllegalArgumentException if {@code pageCount} is below one
     */
    public GitLabPage(
            final List<T> items,
            final GitLabPageWalk<T> walk,
            final int pageCount,
            final @Nullable Integer totalCount) {
        this.items = ListUtils.copyOf(items, "items");
        this.walk = requireNonNull(walk, "walk");
        this.pageCount = requireAtLeast(pageCount, 1, "pageCount");
        this.totalCount = totalCount;
    }

    /**
     * The items of this page.
     *
     * @return the items, possibly empty; a copy that cannot be changed
     */
    @Override
    public List<T> items() {
        return items;
    }

    /**
     * The total number of items in the list.
     *
     * @return the number from {@code X-Total}, or {@code null} if GitLab left it out, as it does for a list of more
     *     than 10,000 records
     */
    public @Nullable Integer totalCount() {
        return totalCount;
    }

    /**
     * The number of this page in its walk.
     *
     * @return the number, counted from one
     */
    public int pageCount() {
        return pageCount;
    }

    /**
     * Requests the page after this one.
     *
     * @return the next page, or empty if this is the last
     * @throws IOException if the request fails
     * @throws IllegalStateException if the walk has moved past this page
     * @throws io.github.piotr_janczukowicz.pagination.PaginationException if a link leaves the API or the walk would
     *     not end
     */
    @Override
    public Optional<GitLabPage<T>> next() throws IOException {
        walk.requireLatest(this);
        return walk.loadNext(this);
    }

    /**
     * Every item of this page and of the pages after it, read eagerly. The number of pages is limited only if
     * {@code PaginationConfiguration.maxPages} is set, so set it when the server is not trusted. A walk goes forward
     * only, so read it once: a second call on the same first page reads that page and then fails with an
     * {@link IllegalStateException} where the second walk would advance.
     *
     * @return all the items
     * @throws IOException if a request fails
     */
    public List<T> all() throws IOException {
        return Pages.all(this);
    }

    /**
     * The items of this page and of the pages after it, requested lazily. A failure to load a page surfaces as an
     * {@link java.io.UncheckedIOException} from the stream operation. A walk goes forward only, so read it once: a
     * second call on the same first page reads that page and then fails with an {@link IllegalStateException} where the
     * second walk would advance.
     *
     * @return a sequential stream of items
     */
    public Stream<T> stream() {
        return Pages.items(this);
    }

    /**
     * This page and the pages after it, requested lazily. A failure to load a page surfaces as an
     * {@link java.io.UncheckedIOException} from the stream operation. A walk goes forward only, so read it once: a
     * second call on the same first page reads that page and then fails with an {@link IllegalStateException} where the
     * second walk would advance.
     *
     * @return a sequential stream of pages
     */
    public Stream<GitLabPage<T>> pages() {
        return Pages.pages(this);
    }

    /**
     * Passes this page and the pages after it to {@code consumer}.
     *
     * @param consumer receives each page in order
     * @throws IOException if a request fails
     */
    public void forEachPage(final Consumer<GitLabPage<T>> consumer) throws IOException {
        Pages.forEachPage(this, consumer);
    }
}
