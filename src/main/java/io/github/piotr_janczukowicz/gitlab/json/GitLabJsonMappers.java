package io.github.piotr_janczukowicz.gitlab.json;

import io.github.piotr_janczukowicz.json.jackson.JsonMappers;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;

/** The mapper that reads and writes GitLab JSON: the shared settings plus the modules that providers register. */
final class GitLabJsonMappers {

    private static final JsonMapper DEFAULTS = JsonMappers.snakeCase()
            .addModule(GitLabCoreModule.create())
            .addModules(serviceModules())
            .build();

    private GitLabJsonMappers() {
        throw new UnsupportedOperationException("Utility class");
    }

    static JsonMapper defaults() {
        return DEFAULTS;
    }

    static List<JacksonModule> modulesOf(final Iterable<? extends GitLabJsonModule> providers) {
        final List<JacksonModule> modules = new ArrayList<>();
        for (final GitLabJsonModule provider : providers) {
            modules.add(provider.module());
        }
        return List.copyOf(modules);
    }

    private static List<JacksonModule> serviceModules() {
        return modulesOf(ServiceLoader.load(GitLabJsonModule.class));
    }
}
