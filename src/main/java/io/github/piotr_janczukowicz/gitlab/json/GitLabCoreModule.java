package io.github.piotr_janczukowicz.gitlab.json;

import io.github.piotr_janczukowicz.gitlab.GitLabErrorBody;
import tools.jackson.databind.module.SimpleModule;

/** The Jackson module for the types of the client packages themselves, as opposed to the generated models. */
final class GitLabCoreModule {

    private GitLabCoreModule() {
        throw new UnsupportedOperationException("Utility class");
    }

    static SimpleModule create() {
        final SimpleModule module = new SimpleModule("GitLabCore");
        module.addDeserializer(GitLabErrorBody.class, new GitLabErrorBodyDeserializer());
        return module;
    }
}
