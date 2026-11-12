package io.github.piotr_janczukowicz.gitlab.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

@UnitTest
class GitLabJsonMappersTest {

    @Test
    void modulesOf_shouldCollectTheModuleOfEveryProvider_whenProvidersAreGiven() {
        final JacksonModule first = new SimpleModule("first");
        final JacksonModule second = new SimpleModule("second");
        final List<GitLabJsonModule> providers = List.of(() -> first, () -> second);

        final List<JacksonModule> modules = GitLabJsonMappers.modulesOf(providers);

        assertThat(modules).containsExactly(first, second);
    }

    @Test
    void modulesOf_shouldReturnAnImmutableList_whenProvidersAreGiven() {
        final List<GitLabJsonModule> providers = List.of(() -> new SimpleModule("only"));
        final List<JacksonModule> modules = GitLabJsonMappers.modulesOf(providers);

        final Throwable thrown = catchThrowable(() -> modules.add(new SimpleModule("late")));

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void modulesOf_shouldReturnNoModules_whenThereAreNoProviders() {
        final List<JacksonModule> modules = GitLabJsonMappers.modulesOf(List.of());

        assertThat(modules).isEmpty();
    }

    @Test
    void defaults_shouldReturnSameMapper_whenCalledTwice() {
        final Object first = GitLabJsonMappers.defaults();

        final Object second = GitLabJsonMappers.defaults();

        assertThat(second).isSameAs(first);
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabJsonMappers> constructor = GitLabJsonMappers.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
