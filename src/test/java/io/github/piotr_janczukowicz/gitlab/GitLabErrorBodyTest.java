package io.github.piotr_janczukowicz.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.piotr_janczukowicz.gitlab.GitLabErrorBody.FieldError;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabErrorBodyTest {

    @Test
    void constructor_shouldCopyTheFieldErrors_whenTheListIsChangedLater() {
        final ArrayList<FieldError> errors = new ArrayList<>();
        errors.add(new FieldError("a", List.of("x")));

        final GitLabErrorBody body = new GitLabErrorBody(null, errors, null, null);
        errors.add(new FieldError("b", List.of("y")));

        assertThat(body.fieldErrors()).hasSize(1);
    }
}
