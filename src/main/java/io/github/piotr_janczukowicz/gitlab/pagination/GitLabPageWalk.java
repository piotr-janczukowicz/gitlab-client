package io.github.piotr_janczukowicz.gitlab.pagination;

import java.io.IOException;
import java.util.Optional;

/**
 * How a {@link GitLabPage} gets to the page after it. The client packages provide it; a page keeps it to load the next
 * page on request.
 *
 * @param <T> type of an item
 */
public interface GitLabPageWalk<T> {

    /**
     * Loads the page after {@code current}.
     *
     * @param current the page to advance from
     * @return the next page, or empty if {@code current} is the last
     * @throws IOException if the request fails
     */
    Optional<GitLabPage<T>> loadNext(GitLabPage<T> current) throws IOException;

    /**
     * Checks that {@code page} is the latest page of the walk. A walk goes forward only, so it cannot advance from a
     * page that it has already moved past.
     *
     * @param page the page that is about to advance
     * @throws IllegalStateException if the walk has moved past {@code page}
     */
    void requireLatest(GitLabPage<T> page);
}
