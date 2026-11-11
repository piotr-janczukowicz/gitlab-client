package io.github.piotr_janczukowicz.gitlab.client.exception;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * What GitLab found wrong with one field of a request. A {@code null} argument is refused with a
 * {@link NullPointerException}.
 *
 * @param field name of the field; a field of an embedded entity is named with a dotted path such as {@code owner.name}
 * @param messages what is wrong with it, as GitLab words it; a copy that cannot be changed
 */
public record GitLabValidationError(String field, List<String> messages) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public GitLabValidationError {
        requireNonNull(field, "field");
        messages = List.copyOf(requireNonNull(messages, "messages"));
    }
}
