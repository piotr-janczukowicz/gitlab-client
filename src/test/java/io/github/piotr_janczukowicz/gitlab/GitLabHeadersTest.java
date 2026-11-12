package io.github.piotr_janczukowicz.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabHeadersTest {

    @Test
    void sudo_shouldBeTheHeaderNameGitLabDocuments() {
        assertThat(GitLabHeaders.SUDO).isEqualTo("Sudo");
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabHeaders> constructor = GitLabHeaders.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
