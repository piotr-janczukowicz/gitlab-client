package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabPageTargetTest {

    @Test
    void constructor_shouldCopyTheQueryAndKeepItsOrder_whenTheSourceChangesLater() {
        final Map<String, List<String>> source = new LinkedHashMap<>();
        source.put("b", new ArrayList<>(List.of("1")));
        source.put("a", new ArrayList<>(List.of("2")));

        final GitLabPageTarget target = new GitLabPageTarget("/projects", source, HttpHeaders.empty());
        source.get("b").add("3");
        source.put("c", List.of("4"));
        final Throwable thrown = catchThrowable(() -> target.query().put("d", List.of()));

        assertSoftly(softly -> {
            softly.assertThat(target.query().keySet()).containsExactly("b", "a");
            softly.assertThat(target.query().get("b")).containsExactly("1");
            softly.assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
        });
    }

    @Test
    void constructor_shouldRefuse_whenAnArgumentIsNull() {
        final Throwable noPath = catchThrowable(() -> new GitLabPageTarget(null, Map.of(), HttpHeaders.empty()));
        final Throwable noQuery = catchThrowable(() -> new GitLabPageTarget("/p", null, HttpHeaders.empty()));
        final Throwable noHeaders = catchThrowable(() -> new GitLabPageTarget("/p", Map.of(), null));

        assertSoftly(softly -> {
            softly.assertThat(noPath).isInstanceOf(NullPointerException.class);
            softly.assertThat(noQuery).isInstanceOf(NullPointerException.class);
            softly.assertThat(noHeaders).isInstanceOf(NullPointerException.class);
        });
    }

    @Test
    void accessors_shouldReturnWhatWasGiven_whenTheTargetIsBuilt() {
        final HttpHeaders headers = HttpHeaders.of("Sudo", "alice");

        final GitLabPageTarget target = new GitLabPageTarget("/projects", Map.of(), headers);

        assertSoftly(softly -> {
            softly.assertThat(target.path()).isEqualTo("/projects");
            softly.assertThat(target.overlayHeaders()).isEqualTo(headers);
        });
    }
}
