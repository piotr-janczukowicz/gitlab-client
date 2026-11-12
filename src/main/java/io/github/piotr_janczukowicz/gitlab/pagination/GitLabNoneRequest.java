package io.github.piotr_janczukowicz.gitlab.pagination;

import java.util.Map;
import java.util.OptionalInt;

/** A request that adds no pagination parameter. */
public record GitLabNoneRequest() implements GitLabPageRequest {

    @Override
    public OptionalInt explicitPerPage() {
        return OptionalInt.empty();
    }

    @Override
    public void putQueryParameters(final Map<String, String> query) {
        // The server's defaults apply: there is nothing to add.
    }
}
