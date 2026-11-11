package io.github.piotr_janczukowicz.gitlab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabTierException;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.reflect.Method;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@UnitTest
class GitLabTierGuardTest {

    private static final URI SELF_MANAGED_ROOT = URI.create("https://gitlab.example.test");

    @ParameterizedTest
    @EnumSource(
            value = GitLabFlavor.class,
            names = {"SELF_MANAGED", "DEDICATED"})
    void requireTier_shouldRefuse_whenTheConfiguredTierIsLowerThanTheRequiredOne(final GitLabFlavor flavor) {
        final GitLabCaller caller = caller(flavor, GitLabTier.FREE);

        final Throwable thrown = catchThrowable(() -> caller.requireTier(GitLabTier.PREMIUM, "getGroupSaml"));

        assertThat(thrown)
                .isInstanceOf(GitLabTierException.class)
                .hasMessageContaining("getGroupSaml")
                .hasMessageContaining("PREMIUM")
                .hasMessageContaining("FREE");
    }

    @Test
    void requireTier_shouldExposeTheOperationAndBothTiers_whenItRefuses() {
        final GitLabCaller caller = caller(GitLabFlavor.SELF_MANAGED, GitLabTier.PREMIUM);

        final Throwable thrown = catchThrowable(() -> caller.requireTier(GitLabTier.ULTIMATE, "listEpics"));

        final GitLabTierException exception = (GitLabTierException) thrown;
        assertSoftly(softly -> {
            softly.assertThat(exception.operation()).isEqualTo("listEpics");
            softly.assertThat(exception.required()).isEqualTo(GitLabTier.ULTIMATE);
            softly.assertThat(exception.configured()).isEqualTo(GitLabTier.PREMIUM);
        });
    }

    @Test
    void requireTier_shouldProceed_whenTheConfiguredTierIncludesTheRequiredOne() {
        final GitLabCaller caller = caller(GitLabFlavor.SELF_MANAGED, GitLabTier.ULTIMATE);

        final Throwable thrown = catchThrowable(() -> caller.requireTier(GitLabTier.PREMIUM, "getGroupSaml"));

        assertThat(thrown).isNull();
    }

    @Test
    void requireTier_shouldProceed_whenNoTierIsConfigured() {
        final GitLabCaller caller = caller(GitLabFlavor.SELF_MANAGED, null);

        final Throwable thrown = catchThrowable(() -> caller.requireTier(GitLabTier.ULTIMATE, "listEpics"));

        assertThat(thrown).isNull();
    }

    @Test
    void requireTier_shouldProceed_whenTheClientTargetsGitLabCom() {
        final GitLabCaller caller = GitLabCaller.of(GitLabClients.create(AuthenticationProviders.none(), "test/1.0"));

        final Throwable thrown = catchThrowable(() -> caller.requireTier(GitLabTier.ULTIMATE, "listEpics"));

        assertThat(thrown).isNull();
    }

    @Test
    void requireTier_shouldRefuse_whenAnArgumentIsNullOrBlank() {
        final GitLabCaller caller = caller(GitLabFlavor.SELF_MANAGED, GitLabTier.FREE);

        final Throwable noTier = catchThrowable(() -> caller.requireTier(null, "op"));
        final Throwable noOperation = catchThrowable(() -> caller.requireTier(GitLabTier.PREMIUM, null));
        final Throwable blankOperation = catchThrowable(() -> caller.requireTier(GitLabTier.PREMIUM, " "));

        assertSoftly(softly -> {
            softly.assertThat(noTier).isInstanceOf(NullPointerException.class);
            softly.assertThat(noOperation).isInstanceOf(NullPointerException.class);
            softly.assertThat(blankOperation).isInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void tierException_shouldRefuseNullArguments_whenCreated() {
        final Throwable noOperation =
                catchThrowable(() -> new GitLabTierException(null, GitLabTier.PREMIUM, GitLabTier.FREE));
        final Throwable noRequired = catchThrowable(() -> new GitLabTierException("op", null, GitLabTier.FREE));
        final Throwable noConfigured = catchThrowable(() -> new GitLabTierException("op", GitLabTier.PREMIUM, null));

        assertSoftly(softly -> {
            softly.assertThat(noOperation).isInstanceOf(NullPointerException.class);
            softly.assertThat(noRequired).isInstanceOf(NullPointerException.class);
            softly.assertThat(noConfigured).isInstanceOf(NullPointerException.class);
        });
    }

    @Test
    void requiresTier_shouldBeReadableAtRuntimeFromAMethod_whenAnOperationIsAnnotated() throws NoSuchMethodException {
        final Method method = Annotated.class.getDeclaredMethod("premiumOperation");

        final RequiresTier annotation = method.getAnnotation(RequiresTier.class);

        assertThat(annotation.value()).isEqualTo(GitLabTier.PREMIUM);
    }

    @ParameterizedTest
    @EnumSource(GitLabTier.class)
    void build_shouldRefuseATier_whenTheBaseUriIsGitLabCom(final GitLabTier tier) {
        final GitLabClientConfiguration.Builder builder =
                GitLabClientConfiguration.builder().tier(tier);

        final Throwable thrown = catchThrowable(builder::build);

        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("top-level group")
                .hasMessageContaining("gitlab.com");
    }

    @Test
    void build_shouldRefuseATier_whenTheHostIsGitLabComInUpperCase() {
        final GitLabClientConfiguration.Builder builder = GitLabClientConfiguration.builder()
                .baseUri(URI.create("https://GITLAB.COM"))
                .tier(GitLabTier.PREMIUM);

        final Throwable thrown = catchThrowable(builder::build);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("top-level group");
    }

    @Test
    void build_shouldRefuseATier_whenTheFlavorIsExplicitlyGitLabCom() {
        final GitLabClientConfiguration.Builder builder = GitLabClientConfiguration.builder()
                .baseUri(SELF_MANAGED_ROOT)
                .flavor(GitLabFlavor.GITLAB_COM)
                .tier(GitLabTier.PREMIUM);

        final Throwable thrown = catchThrowable(builder::build);

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("top-level group");
    }

    @Test
    void build_shouldAcceptATier_whenTheFlavorIsSelfManagedOnAnyHost() {
        final GitLabClientConfiguration.Builder builder = GitLabClientConfiguration.builder()
                .baseUri(URI.create("https://gitlab.com"))
                .flavor(GitLabFlavor.SELF_MANAGED)
                .tier(GitLabTier.PREMIUM);

        final GitLabClientConfiguration configuration = builder.build();

        assertThat(configuration.tier()).contains(GitLabTier.PREMIUM);
    }

    @Test
    void build_shouldAcceptNoTier_whenTheBaseUriIsGitLabCom() {
        final GitLabClientConfiguration.Builder builder = GitLabClientConfiguration.builder();

        final GitLabClientConfiguration configuration = builder.build();

        assertThat(configuration.tier()).isEmpty();
    }

    private static GitLabCaller caller(final GitLabFlavor flavor, final GitLabTier tier) {
        final GitLabClientConfiguration configuration = GitLabClientConfiguration.builder()
                .baseUri(SELF_MANAGED_ROOT)
                .flavor(flavor)
                .tier(tier)
                .build();
        return GitLabCaller.of(GitLabClients.create(AuthenticationProviders.none(), "test/1.0", configuration));
    }

    private static final class Annotated {

        @RequiresTier(GitLabTier.PREMIUM)
        void premiumOperation() {
            // Carries the annotation only.
        }
    }
}
