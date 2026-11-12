package io.github.piotr_janczukowicz.gitlab.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class GitLabTierTest {

    @ParameterizedTest
    @CsvSource({
        "FREE,FREE,true",
        "FREE,PREMIUM,false",
        "FREE,ULTIMATE,false",
        "PREMIUM,FREE,true",
        "PREMIUM,PREMIUM,true",
        "PREMIUM,ULTIMATE,false",
        "ULTIMATE,FREE,true",
        "ULTIMATE,PREMIUM,true",
        "ULTIMATE,ULTIMATE,true"
    })
    void includes_shouldBeTrueOnlyForAnEqualOrLowerRequirement_whenTiersAreCompared(
            final GitLabTier configured, final GitLabTier required, final boolean expected) {
        final boolean includes = configured.includes(required);

        assertThat(includes).isEqualTo(expected);
    }
}
