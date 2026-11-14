package io.github.piotr_janczukowicz.gitlab.client.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.http.HttpBody;
import io.github.piotr_janczukowicz.http.HttpHeaders;
import io.github.piotr_janczukowicz.http.HttpResponse;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabPageHeadersTest {

    @ParameterizedTest
    @CsvSource({"2,2", "' 7 ',7", "100,100"})
    void nextPage_shouldReadAPositiveWholeNumber_whenTheHeaderHoldsOne(final String value, final int expected) {
        final HttpResponse response = response("X-Next-Page", value);

        final Integer page = GitLabPageHeaders.nextPage(response);

        assertThat(page).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "0", "-3", "abc", "1.5", "99999999999999999999", "+3"})
    void nextPage_shouldBeNull_whenTheHeaderIsNotAPositiveWholeNumber(final String value) {
        final HttpResponse response = response("X-Next-Page", value);

        final Integer page = GitLabPageHeaders.nextPage(response);

        assertThat(page).isNull();
    }

    @Test
    void nextPage_shouldBeNull_whenTheHeaderIsMissing() {
        assertThat(GitLabPageHeaders.nextPage(response())).isNull();
    }

    @ParameterizedTest
    @CsvSource({"0,0", "42,42", "' 9 ',9"})
    void total_shouldReadANonNegativeWholeNumber_whenTheHeaderHoldsOne(final String value, final int expected) {
        final HttpResponse response = response("X-Total", value);

        final Integer total = GitLabPageHeaders.total(response);

        assertThat(total).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-1", "x", "2.5"})
    void total_shouldBeNull_whenTheHeaderIsNotANonNegativeWholeNumber(final String value) {
        final HttpResponse response = response("X-Total", value);

        final Integer total = GitLabPageHeaders.total(response);

        assertThat(total).isNull();
    }

    @Test
    void total_shouldBeNull_whenTheHeaderIsMissing() {
        assertThat(GitLabPageHeaders.total(response())).isNull();
    }

    @Test
    void constructor_shouldRejectInstantiation_whenCalledViaReflection() throws Exception {
        final Constructor<GitLabPageHeaders> constructor = GitLabPageHeaders.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }

    private static HttpResponse response(final String... headers) {
        return new HttpResponse(200, HttpHeaders.of(headers), HttpBody.absent());
    }
}
