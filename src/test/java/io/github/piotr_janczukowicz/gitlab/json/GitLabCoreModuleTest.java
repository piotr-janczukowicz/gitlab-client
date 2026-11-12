package io.github.piotr_janczukowicz.gitlab.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.module.SimpleModule;

@UnitTest
class GitLabCoreModuleTest {

    @Test
    void create_shouldNameTheModule_whenCreated() {
        final SimpleModule module = GitLabCoreModule.create();

        assertThat(module.getModuleName()).isEqualTo("GitLabCore");
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabCoreModule> constructor = GitLabCoreModule.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
