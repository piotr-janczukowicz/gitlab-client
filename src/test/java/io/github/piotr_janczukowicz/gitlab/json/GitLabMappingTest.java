package io.github.piotr_janczukowicz.gitlab.json;

import io.github.piotr_janczukowicz.json.JsonCodec;
import io.github.piotr_janczukowicz.testsupport.mapping.AbstractMappingTest;

abstract class GitLabMappingTest extends AbstractMappingTest {

    @Override
    protected JsonCodec codec() {
        return GitLabJsonCodecs.defaults();
    }
}
