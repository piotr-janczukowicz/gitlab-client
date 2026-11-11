package io.github.piotr_janczukowicz.gitlab.json;

import io.github.piotr_janczukowicz.json.JsonCodec;
import io.github.piotr_janczukowicz.json.jackson.JacksonJsonCodec;

/** The {@link JsonCodec} for GitLab payloads. */
public final class GitLabJsonCodecs {

    private static final JsonCodec DEFAULTS = new JacksonJsonCodec(GitLabJsonMappers.defaults());

    private GitLabJsonCodecs() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The codec that the client packages use. It reads {@code snake_case} names as record components, ignores
     * properties a type does not have, reads every timestamp shape that GitLab sends, and decodes collections as
     * immutable ones. It also knows the models of every {@link GitLabJsonModule} that a provider registers.
     *
     * @return the shared, thread-safe codec; the same instance on every call
     */
    public static JsonCodec defaults() {
        return DEFAULTS;
    }
}
