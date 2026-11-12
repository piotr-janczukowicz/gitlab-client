package io.github.piotr_janczukowicz.gitlab.json;

import tools.jackson.databind.JacksonModule;

/**
 * Extension point through which a library that adds GitLab models, such as the generated REST API, registers the
 * Jackson module that reads and writes them. Provide it with a {@code provides} directive of the module descriptor, or
 * with a {@code META-INF/services} entry on the class path; every provider found is added to the mapper that
 * {@link GitLabJsonCodecs#defaults()} uses.
 */
public interface GitLabJsonModule {

    /**
     * The Jackson module of the provider's models.
     *
     * @return the module; the mapper registers it once
     */
    JacksonModule module();
}
