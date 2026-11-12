package io.github.piotr_janczukowicz.gitlab.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.GitLabErrorBody;
import io.github.piotr_janczukowicz.gitlab.GitLabErrorBody.FieldError;
import io.github.piotr_janczukowicz.json.JsonCodec;
import io.github.piotr_janczukowicz.json.JsonDecodeException;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabErrorBodyDeserializerTest {

    private final JsonCodec codec = GitLabJsonCodecs.defaults();

    @Test
    void decode_shouldReadAStringMessage_whenAnAttributeIsMissing() {
        final byte[] json = bytes("{\"message\":\"400 (Bad request) \\\"title\\\" not given\"}");

        final GitLabErrorBody body = codec.decode(json, GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.message()).isEqualTo("400 (Bad request) \"title\" not given");
            softly.assertThat(body.fieldErrors()).isEmpty();
            softly.assertThat(body.error()).isNull();
            softly.assertThat(body.errorDescription()).isNull();
        });
    }

    @Test
    void decode_shouldReadFieldsAsFieldErrors_whenMessageMapsFieldsToLists() {
        final byte[] json = bytes("{\"message\":{\"bio\":[\"is too long (maximum is 255 characters)\"]}}");

        final GitLabErrorBody body = codec.decode(json, GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.message()).isNull();
            softly.assertThat(body.fieldErrors())
                    .containsExactly(new FieldError("bio", List.of("is too long (maximum is 255 characters)")));
        });
    }

    @Test
    void decode_shouldReadAStringValueAsOneMessage_whenMessageMapsFieldsToStrings() {
        final byte[] json = bytes("{\"message\":{\"error\":\"Your snippet has been recognized as spam.\"},"
                + "\"needs_captcha_response\":true,\"spam_log_id\":42,\"captcha_site_key\":\"REDACTED\"}");

        final GitLabErrorBody body = codec.decode(json, GitLabErrorBody.class);

        assertThat(body.fieldErrors())
                .containsExactly(new FieldError("error", List.of("Your snippet has been recognized as spam.")));
    }

    @Test
    void decode_shouldNameNestedFieldsWithADottedPath_whenMessageEmbedsAnEntity() {
        final byte[] json = bytes("{\"message\":{\"owner\":{\"name\":[\"is blank\"],\"email\":[\"is invalid\"]},"
                + "\"title\":[\"is too short\"]}}");

        final GitLabErrorBody body = codec.decode(json, GitLabErrorBody.class);

        assertThat(body.fieldErrors())
                .containsExactly(
                        new FieldError("owner.name", List.of("is blank")),
                        new FieldError("owner.email", List.of("is invalid")),
                        new FieldError("title", List.of("is too short")));
    }

    @Test
    void decode_shouldJoinTheItemsAsOneMessage_whenMessageIsAnArray() {
        final byte[] json = bytes("{\"message\":[\"first\",\"second\"]}");

        final GitLabErrorBody body = codec.decode(json, GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.message()).isEqualTo("first, second");
            softly.assertThat(body.fieldErrors()).isEmpty();
        });
    }

    @Test
    void decode_shouldReadErrorAndDescription_whenBodyIsAnOAuthError() {
        final byte[] json = bytes("{\"error\":\"invalid_token\",\"error_description\":\"The access token expired\"}");

        final GitLabErrorBody body = codec.decode(json, GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.error()).isEqualTo("invalid_token");
            softly.assertThat(body.errorDescription()).isEqualTo("The access token expired");
            softly.assertThat(body.message()).isNull();
        });
    }

    @Test
    void decode_shouldIgnoreValuesItCannotUse_whenMessageHoldsOtherShapes() {
        final byte[] json = bytes("{\"message\":{\"count\":3,\"flag\":true,\"nothing\":null,\"tags\":[1,\"a\",null]}}");

        final GitLabErrorBody body = codec.decode(json, GitLabErrorBody.class);

        assertThat(body.fieldErrors()).containsExactly(new FieldError("tags", List.of("a")));
    }

    @Test
    void decode_shouldKeepNothing_whenBodyIsAnEmptyObject() {
        final GitLabErrorBody body = codec.decode(bytes("{}"), GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.message()).isNull();
            softly.assertThat(body.fieldErrors()).isEmpty();
            softly.assertThat(body.error()).isNull();
            softly.assertThat(body.errorDescription()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"[\"a\"]", "\"text\"", "42", "true"})
    void decode_shouldFail_whenBodyIsNotAJsonObject(final String json) {
        final byte[] bytes = bytes(json);

        final Throwable thrown = catchThrowable(() -> codec.decode(bytes, GitLabErrorBody.class));

        assertThat(thrown).isInstanceOf(JsonDecodeException.class);
    }

    @Test
    void constructor_shouldCopyTheFieldErrors_whenTheListIsChangedLater() {
        final ArrayList<FieldError> errors = new ArrayList<>();
        errors.add(new FieldError("a", List.of("x")));

        final GitLabErrorBody body = new GitLabErrorBody(null, errors, null, null);
        errors.add(new FieldError("b", List.of("y")));

        assertThat(body.fieldErrors()).hasSize(1);
    }

    private static byte[] bytes(final String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
