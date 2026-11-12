package io.github.piotr_janczukowicz.gitlab;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;

import io.github.piotr_janczukowicz.util.ListUtils;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The body of a GitLab error response, in whichever of the shapes GitLab uses (<a
 * href="https://docs.gitlab.com/api/rest/troubleshooting/">troubleshooting</a>): {@code message} as text, as a map of
 * fields to their messages, or as a list of texts; and {@code error} with {@code error_description} for an OAuth error.
 * A {@code null} {@code fieldErrors} is refused with a {@link NullPointerException}.
 *
 * @param message the text of {@code message}, or {@code null} if it is a map or absent; a list is joined with commas
 * @param fieldErrors the fields of a {@code message} that is a map, in body order; a copy that cannot be changed
 * @param error the {@code error} of an OAuth error, or {@code null}
 * @param errorDescription the {@code error_description} of an OAuth error, or {@code null}
 */
public record GitLabErrorBody(
        @Nullable String message,
        List<FieldError> fieldErrors,
        @Nullable String error,
        @Nullable String errorDescription) {

    public GitLabErrorBody {
        fieldErrors = ListUtils.copyOf(fieldErrors, "fieldErrors");
    }

    /**
     * The messages of one field.
     *
     * @param field name of the field; a field of an embedded entity has a dotted path such as {@code owner.name}
     * @param messages what is wrong with it; a copy that cannot be changed
     */
    public record FieldError(String field, List<String> messages) {

        public FieldError {
            requireNonNull(field, "field");
            messages = List.copyOf(requireNonNull(messages, "messages"));
        }
    }
}
