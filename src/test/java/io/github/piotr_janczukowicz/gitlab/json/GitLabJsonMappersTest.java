package io.github.piotr_janczukowicz.gitlab.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabJsonMappersTest {

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
