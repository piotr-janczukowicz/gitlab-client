package io.github.piotr_janczukowicz.gitlab.json;

import io.github.piotr_janczukowicz.gitlab.GitLabErrorBody;
import io.github.piotr_janczukowicz.gitlab.GitLabErrorBody.FieldError;
import io.github.piotr_janczukowicz.json.jackson.JsonNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Reads the body of a GitLab error response. GitLab words {@code message} three ways, as text, as a list of texts, and
 * as a map of fields to their messages, where the messages are a list, one text, or an embedded entity with fields of
 * its own. Values of any other shape are left out, because an error body must never make the error itself fail.
 */
final class GitLabErrorBodyDeserializer extends StdDeserializer<GitLabErrorBody> {

    private static final String MESSAGE = "message";
    private static final String PATH_SEPARATOR = ".";
    private static final String LIST_SEPARATOR = ", ";

    GitLabErrorBodyDeserializer() {
        super(GitLabErrorBody.class);
    }

    @Override
    public GitLabErrorBody deserialize(final JsonParser parser, final DeserializationContext context) {
        final JsonNode tree = context.readValue(parser, JsonNode.class);
        if (!tree.isObject()) {
            throw DatabindException.from(parser, "GitLab error body must be a JSON object");
        }
        final JsonNode message = tree.get(MESSAGE);
        return new GitLabErrorBody(
                messageText(message),
                fieldErrors(message),
                JsonNodes.textOrNull(tree, "error"),
                JsonNodes.textOrNull(tree, "error_description"));
    }

    private static @Nullable String messageText(final @Nullable JsonNode message) {
        if (message == null) {
            return null;
        }
        if (message.isString()) {
            return message.asString();
        }
        if (message.isArray()) {
            final List<String> texts = texts(message);
            return texts.isEmpty() ? null : String.join(LIST_SEPARATOR, texts);
        }
        return null;
    }

    private static List<FieldError> fieldErrors(final @Nullable JsonNode message) {
        if (message == null || !message.isObject()) {
            return List.of();
        }
        final List<FieldError> errors = new ArrayList<>();
        collect("", message, errors);
        return List.copyOf(errors);
    }

    private static void collect(final String prefix, final JsonNode object, final List<FieldError> errors) {
        for (final Map.Entry<String, JsonNode> property : object.properties()) {
            final String path = prefix.isEmpty() ? property.getKey() : prefix + PATH_SEPARATOR + property.getKey();
            final JsonNode value = property.getValue();
            if (value.isObject()) {
                collect(path, value, errors);
            } else {
                final List<String> messages = value.isString() ? List.of(value.asString()) : texts(value);
                if (!messages.isEmpty()) {
                    errors.add(new FieldError(path, messages));
                }
            }
        }
    }

    private static List<String> texts(final JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        final List<String> texts = new ArrayList<>();
        for (final JsonNode item : array) {
            if (item.isString()) {
                texts.add(item.asString());
            }
        }
        return List.copyOf(texts);
    }
}
