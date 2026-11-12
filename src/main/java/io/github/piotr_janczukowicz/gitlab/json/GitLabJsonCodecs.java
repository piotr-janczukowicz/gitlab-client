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
     * <p>A property that the JSON leaves out is not an error: a primitive record component then reads as {@code 0} or
     * {@code false}, so a model that must tell "absent" from zero uses a boxed type. A property that is present with
     * the value {@code null} for a primitive component does fail the decode. An enum constant that the library does not
     * know reads as {@code null}.
     *
     * @return the shared, thread-safe codec; the same instance on every call
     */
    public static JsonCodec defaults() {
        return DEFAULTS;
    }
}
