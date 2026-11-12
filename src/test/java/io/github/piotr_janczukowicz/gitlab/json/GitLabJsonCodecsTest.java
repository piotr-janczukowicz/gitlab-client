package io.github.piotr_janczukowicz.gitlab.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.json.JsonCodec;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabJsonCodecsTest {

    record Project(long id, String pathWithNamespace, Instant createdAt, List<String> topics) {}

    @Test
    void defaults_shouldReturnSameInstance_whenCalledTwice() {
        final JsonCodec first = GitLabJsonCodecs.defaults();

        final JsonCodec second = GitLabJsonCodecs.defaults();

        assertThat(second).isSameAs(first);
    }

    @Test
    void decode_shouldMapSnakeCaseNamesToRecordComponents_whenBodyIsAGitLabProject() {
        final JsonCodec codec = GitLabJsonCodecs.defaults();
        final byte[] json = """
                {"id": 7, "path_with_namespace": "group/sub/project", "created_at": "2024-02-29T23:59:59.123Z",
                 "topics": ["java", "gitlab"], "something_new": true}
                """.getBytes(StandardCharsets.UTF_8);

        final Project project = codec.decode(json, Project.class);

        assertSoftly(softly -> {
            softly.assertThat(project.id()).isEqualTo(7L);
            softly.assertThat(project.pathWithNamespace()).isEqualTo("group/sub/project");
            softly.assertThat(project.createdAt()).isEqualTo(Instant.parse("2024-02-29T23:59:59.123Z"));
            softly.assertThat(project.topics()).containsExactly("java", "gitlab");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-01-02T03:04:05Z", "2024-01-02T03:04:05.678Z", "2024-01-02T03:04:05.678+02:00"})
    void decode_shouldReadEveryTimestampShapeGitLabSends_whenFieldIsAnInstant(final String text) {
        final JsonCodec codec = GitLabJsonCodecs.defaults();
        final byte[] json = ("{\"id\":1,\"path_with_namespace\":\"a/b\",\"created_at\":\"" + text + "\",\"topics\":[]}")
                .getBytes(StandardCharsets.UTF_8);

        final Project project = codec.decode(json, Project.class);

        assertThat(project.createdAt()).isEqualTo(Instant.parse(text));
    }

    @Test
    void decode_shouldReturnAnImmutableList_whenBodyHoldsAnArray() {
        final JsonCodec codec = GitLabJsonCodecs.defaults();
        final byte[] json =
                "{\"id\":1,\"path_with_namespace\":\"a/b\",\"created_at\":\"2024-01-02T03:04:05Z\",\"topics\":[\"x\"]}"
                        .getBytes(StandardCharsets.UTF_8);

        final Project project = codec.decode(json, Project.class);
        final Throwable thrown = catchThrowable(() -> project.topics().add("y"));

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void encode_shouldWriteSnakeCaseNames_whenValueIsARecord() {
        final JsonCodec codec = GitLabJsonCodecs.defaults();
        final Project project = new Project(7, "a/b", Instant.parse("2024-01-02T03:04:05Z"), List.of("java"));

        final String json = new String(codec.encode(project), StandardCharsets.UTF_8);

        assertThat(json)
                .contains("\"path_with_namespace\":\"a/b\"")
                .contains("\"created_at\":\"2024-01-02T03:04:05Z\"");
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabJsonCodecs> constructor = GitLabJsonCodecs.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
