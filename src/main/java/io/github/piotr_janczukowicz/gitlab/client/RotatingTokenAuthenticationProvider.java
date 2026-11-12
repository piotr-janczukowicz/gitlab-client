package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNotBlank;

import io.github.piotr_janczukowicz.http.client.authentication.Authentication;
import io.github.piotr_janczukowicz.http.client.authentication.AuthenticationProvider;
import java.util.Optional;
import java.util.function.Supplier;

final class RotatingTokenAuthenticationProvider implements AuthenticationProvider {

    private final String identity;
    private final Supplier<String> tokens;

    RotatingTokenAuthenticationProvider(final String identity, final Supplier<String> tokens) {
        this.identity = requireNotBlank(identity, "identity");
        this.tokens = tokens;
    }

    @Override
    public Optional<Authentication> authentication() {
        return Optional.of(Authentication.ofBearer(tokens.get()).withIdentity(identity));
    }

    @Override
    public String toString() {
        return "GitLabAuthenticationProviders.rotatingToken(" + identity + ")";
    }
}
