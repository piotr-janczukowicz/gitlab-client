package io.github.piotr_janczukowicz.gitlab.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.GitLabErrorBody;
import io.github.piotr_janczukowicz.gitlab.GitLabErrorBody.FieldError;
import io.github.piotr_janczukowicz.json.JsonDecodeException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GitLabErrorBodyMappingTest extends GitLabMappingTest {

    @Test
    void decode_shouldReadAStringMessage_whenAnAttributeIsMissing() {
        final GitLabErrorBody body = decode("error-body-message-text.json", GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.message()).isEqualTo("400 (Bad request) \"title\" not given");
            softly.assertThat(body.fieldErrors()).isEmpty();
            softly.assertThat(body.error()).isNull();
            softly.assertThat(body.errorDescription()).isNull();
        });
    }

    @Test
    void decode_shouldReadFieldsAsFieldErrors_whenMessageMapsFieldsToLists() {
        final GitLabErrorBody body = decode("error-body-message-fields-to-lists.json", GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.message()).isNull();
            softly.assertThat(body.fieldErrors())
                    .containsExactly(new FieldError("bio", List.of("is too long (maximum is 255 characters)")));
        });
    }

    @Test
    void decode_shouldReadAStringValueAsOneMessage_whenMessageMapsFieldsToStrings() {
        final GitLabErrorBody body = decode("error-body-message-fields-to-strings.json", GitLabErrorBody.class);

        assertThat(body.fieldErrors())
                .containsExactly(new FieldError("error", List.of("Your snippet has been recognized as spam.")));
    }

    @Test
    void decode_shouldNameNestedFieldsWithADottedPath_whenMessageEmbedsAnEntity() {
        final GitLabErrorBody body = decode("error-body-message-embedded-entity.json", GitLabErrorBody.class);

        assertThat(body.fieldErrors())
                .containsExactly(
                        new FieldError("owner.name", List.of("is blank")),
                        new FieldError("owner.email", List.of("is invalid")),
                        new FieldError("title", List.of("is too short")));
    }

    @Test
    void decode_shouldJoinTheItemsAsOneMessage_whenMessageIsAnArray() {
        final GitLabErrorBody body = decode("error-body-message-array.json", GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.message()).isEqualTo("first, second");
            softly.assertThat(body.fieldErrors()).isEmpty();
        });
    }

    @Test
    void decode_shouldReadErrorAndDescription_whenBodyIsAnOAuthError() {
        final GitLabErrorBody body = decode("error-body-oauth-error.json", GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.error()).isEqualTo("invalid_token");
            softly.assertThat(body.errorDescription()).isEqualTo("The access token expired");
            softly.assertThat(body.message()).isNull();
        });
    }

    @Test
    void decode_shouldIgnoreValuesItCannotUse_whenMessageHoldsOtherShapes() {
        final GitLabErrorBody body = decode("error-body-message-other-shapes.json", GitLabErrorBody.class);

        assertThat(body.fieldErrors()).containsExactly(new FieldError("tags", List.of("a")));
    }

    @Test
    void decode_shouldKeepNothing_whenBodyIsAnEmptyObject() {
        final GitLabErrorBody body = decode("error-body-empty-object.json", GitLabErrorBody.class);

        assertSoftly(softly -> {
            softly.assertThat(body.message()).isNull();
            softly.assertThat(body.fieldErrors()).isEmpty();
            softly.assertThat(body.error()).isNull();
            softly.assertThat(body.errorDescription()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "error-body-array.json",
                "error-body-string.json",
                "error-body-number.json",
                "error-body-boolean.json"
            })
    void decode_shouldFail_whenBodyIsNotAJsonObject(final String resource) {
        final Throwable thrown = catchThrowable(() -> decode(resource, GitLabErrorBody.class));

        assertThat(thrown).isInstanceOf(JsonDecodeException.class);
    }
}
