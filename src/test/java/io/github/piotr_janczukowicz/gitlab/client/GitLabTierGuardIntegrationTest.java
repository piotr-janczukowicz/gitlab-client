package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.http.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.gitlab.client.exception.GitLabTierException;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProviders;
import io.github.piotr_janczukowicz.test.http.StubHttpServer;
import io.github.piotr_janczukowicz.testsupport.IntegrationTest;
import io.github.piotr_janczukowicz.uri.PathTemplate;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@IntegrationTest
class GitLabTierGuardIntegrationTest {

    private static final String PATH = "/api/v4/premium";

    @RegisterExtension
    static final StubHttpServer HTTP = new StubHttpServer();

    @Test
    void premiumOperation_shouldSendNoRequest_whenTheConfiguredTierIsFree() {
        HTTP.stub(GET, PATH, 200);

        final Throwable thrown;
        try (GitLabClient client = client(GitLabTier.FREE)) {
            final SampleApi api = new SampleApi(GitLabCaller.of(client));
            thrown = catchThrowable(api::premiumOperation);
        }

        assertThat(thrown).isInstanceOf(GitLabTierException.class);
        HTTP.verifyRequested(GET, PATH, 0);
    }

    @Test
    void premiumOperation_shouldSendTheRequest_whenTheConfiguredTierIsUltimate() throws IOException {
        HTTP.stub(GET, PATH, 200);

        try (GitLabClient client = client(GitLabTier.ULTIMATE)) {
            new SampleApi(GitLabCaller.of(client)).premiumOperation();
        }

        HTTP.verifyRequested(GET, PATH, 1);
    }

    @Test
    void premiumOperation_shouldSendTheRequest_whenNoTierIsConfigured() throws IOException {
        HTTP.stub(GET, PATH, 200);

        try (GitLabClient client = client(null)) {
            new SampleApi(GitLabCaller.of(client)).premiumOperation();
        }

        HTTP.verifyRequested(GET, PATH, 1);
    }

    private static GitLabClient client(final GitLabTier tier) {
        return GitLabClients.create(
                AuthenticationProviders.none(),
                "gitlab-test/1.0",
                GitLabClientConfiguration.builder()
                        .baseUri(HTTP.baseUri())
                        .allowInsecureHttp(true)
                        .flavor(GitLabFlavor.SELF_MANAGED)
                        .tier(tier)
                        .build());
    }

    private static final class SampleApi extends GitLabResourceApi {

        SampleApi(final GitLabCaller caller) {
            super(caller);
        }

        @RequiresTier(GitLabTier.PREMIUM)
        void premiumOperation() throws IOException {
            caller.requireTier(GitLabTier.PREMIUM, "premiumOperation");
            caller.send(get(PathTemplate.of("/premium")));
        }
    }
}
