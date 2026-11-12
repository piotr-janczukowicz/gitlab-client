package io.github.piotr_janczukowicz.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.lang.module.ModuleDescriptor;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

@UnitTest
class ModuleDescriptorTest {

    private static final String ROOT = "io.github.piotr_janczukowicz.gitlab";

    @Test
    void module_shouldBeNamedAfterTheGitLabClient() {
        final Module module = ModuleDescriptorTest.class.getModule();

        assertSoftly(softly -> {
            softly.assertThat(module.isNamed()).isTrue();
            softly.assertThat(module.getName()).isEqualTo("io.github.piotr_janczukowicz.gitlabclient");
        });
    }

    @Test
    void module_shouldExportTheInternalPackageOnlyToTheRestClient_whenExportsAreListed() {
        final ModuleDescriptor descriptor =
                ModuleDescriptorTest.class.getModule().getDescriptor();
        final Set<String> targets = descriptor.exports().stream()
                .filter(exports -> exports.source().equals(ROOT + ".client.internal"))
                .flatMap(exports -> exports.targets().stream())
                .collect(Collectors.toSet());

        assertThat(targets).containsExactly("io.github.piotr_janczukowicz.gitlabrestclient");
    }

    @Test
    void module_shouldExportEveryClientPackage_whenExportsAreListed() {
        final ModuleDescriptor descriptor =
                ModuleDescriptorTest.class.getModule().getDescriptor();
        final Set<String> exported = descriptor.exports().stream()
                .filter(exports -> !exports.isQualified())
                .map(ModuleDescriptor.Exports::source)
                .collect(Collectors.toSet());

        assertThat(exported)
                .containsExactlyInAnyOrder(
                        ROOT,
                        ROOT + ".client",
                        ROOT + ".client.exception",
                        ROOT + ".client.pagination",
                        ROOT + ".client.ratelimit",
                        ROOT + ".json",
                        ROOT + ".pagination");
    }
}
