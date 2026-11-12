package io.github.piotr_janczukowicz.gitlab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.http.client.authentication.Authentication;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProvider;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class GitLabAuthenticationProvidersTest {

    private static final String SECRET = "glpat-very-secret";

    @Test
    void personalToken_shouldSendABearerCredential_whenCreated() {
        final AuthenticationProvider provider = GitLabAuthenticationProviders.personalToken(SECRET);

        final Authentication authentication = provider.authentication().orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(authentication.headerName()).isEqualTo("Authorization");
            softly.assertThat(authentication.headerValue()).isEqualTo("Bearer " + SECRET);
            softly.assertThat(provider.credentialHeaderName()).isEqualTo("Authorization");
        });
    }

    @Test
    void oauth_shouldSendABearerCredential_whenCreated() {
        final AuthenticationProvider provider = GitLabAuthenticationProviders.oauth(SECRET);

        final Authentication authentication = provider.authentication().orElseThrow();

        assertThat(authentication.headerValue()).isEqualTo("Bearer " + SECRET);
    }

    @Test
    void jobToken_shouldSendTheJobTokenHeaderAndNoAuthorization_whenCreated() {
        final AuthenticationProvider provider = GitLabAuthenticationProviders.jobToken(SECRET);

        final Authentication authentication = provider.authentication().orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(authentication.headerName()).isEqualTo("JOB-TOKEN");
            softly.assertThat(authentication.headerValue()).isEqualTo(SECRET);
            softly.assertThat(provider.credentialHeaderName()).isEqualTo("JOB-TOKEN");
        });
    }

    @Test
    void rotatingToken_shouldKeepTheIdentity_whenTheTokenChanges() {
        final AtomicReference<String> token = new AtomicReference<>("first-token");
        final AuthenticationProvider provider = GitLabAuthenticationProviders.rotatingToken("ci-bot", token::get);

        final Authentication before = provider.authentication().orElseThrow();
        token.set("second-token");
        final Authentication after = provider.authentication().orElseThrow();

        assertSoftly(softly -> {
            softly.assertThat(before.headerValue()).isEqualTo("Bearer first-token");
            softly.assertThat(after.headerValue()).isEqualTo("Bearer second-token");
            softly.assertThat(before.identity()).isEqualTo("ci-bot");
            softly.assertThat(after.identity()).isEqualTo("ci-bot");
        });
    }

    @Test
    void rotatingToken_shouldFail_whenTheSupplierReturnsABlankToken() {
        final AuthenticationProvider provider = GitLabAuthenticationProviders.rotatingToken("ci-bot", () -> " ");

        final Throwable thrown = catchThrowable(provider::authentication);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rotatingToken_shouldNotRenewAfterARejection_whenCreated() {
        final AuthenticationProvider provider = GitLabAuthenticationProviders.rotatingToken("ci-bot", () -> SECRET);
        final Authentication rejected = provider.authentication().orElseThrow();

        final boolean isRenewable = provider.isRenewableAfterRejection(rejected);

        assertThat(isRenewable).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void personalToken_shouldRefuse_whenTheTokenIsBlank(final String token) {
        final Throwable thrown = catchThrowable(() -> GitLabAuthenticationProviders.personalToken(token));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void oauth_shouldRefuse_whenTheTokenIsBlank(final String token) {
        final Throwable thrown = catchThrowable(() -> GitLabAuthenticationProviders.oauth(token));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void jobToken_shouldRefuse_whenTheTokenIsBlank(final String token) {
        final Throwable thrown = catchThrowable(() -> GitLabAuthenticationProviders.jobToken(token));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rotatingToken_shouldRefuse_whenAnArgumentIsNullOrTheIdentityIsBlank() {
        final Throwable noIdentity = catchThrowable(() -> GitLabAuthenticationProviders.rotatingToken(null, () -> "t"));
        final Throwable noTokens = catchThrowable(() -> GitLabAuthenticationProviders.rotatingToken("bot", null));
        final Throwable blankIdentity =
                catchThrowable(() -> GitLabAuthenticationProviders.rotatingToken(" ", () -> "t"));

        assertSoftly(softly -> {
            softly.assertThat(noIdentity).isInstanceOf(NullPointerException.class);
            softly.assertThat(noTokens).isInstanceOf(NullPointerException.class);
            softly.assertThat(blankIdentity).isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void toString_shouldNeverShowTheToken_whenAProviderOrItsAuthenticationIsPrinted() {
        final AuthenticationProvider personal = GitLabAuthenticationProviders.personalToken(SECRET);
        final AuthenticationProvider job = GitLabAuthenticationProviders.jobToken(SECRET);
        final AuthenticationProvider rotating = GitLabAuthenticationProviders.rotatingToken("ci-bot", () -> SECRET);

        assertSoftly(softly -> {
            softly.assertThat(personal.toString()).doesNotContain(SECRET);
            softly.assertThat(job.toString()).doesNotContain(SECRET);
            softly.assertThat(rotating.toString()).doesNotContain(SECRET).contains("ci-bot");
            softly.assertThat(personal.authentication().orElseThrow().toString())
                    .doesNotContain(SECRET);
            softly.assertThat(job.authentication().orElseThrow().toString()).doesNotContain(SECRET);
            softly.assertThat(rotating.authentication().orElseThrow().toString())
                    .doesNotContain(SECRET);
        });
    }

    @Test
    void constructor_shouldBeUnreachable_whenCalledByReflection() throws ReflectiveOperationException {
        final Constructor<GitLabAuthenticationProviders> constructor =
                GitLabAuthenticationProviders.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        final Throwable thrown = catchThrowable(constructor::newInstance);

        assertThat(thrown).hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
