// The module that gets the internal package is not on this module's path.
@SuppressWarnings("module")
module io.github.piotr_janczukowicz.gitlabclient {
    requires transitive okhttp3;
    requires okio;
    requires transitive tools.jackson.databind;
    requires org.slf4j;
    requires org.jspecify;
    requires transitive common.core;
    requires static io.soabase.recordbuilder.core;
    requires static java.compiler;

    exports io.github.piotr_janczukowicz.gitlab;
    exports io.github.piotr_janczukowicz.gitlab.client;
    exports io.github.piotr_janczukowicz.gitlab.client.exception;
    exports io.github.piotr_janczukowicz.gitlab.client.internal to
            io.github.piotr_janczukowicz.gitlabrestclient;
    exports io.github.piotr_janczukowicz.gitlab.client.pagination;
    exports io.github.piotr_janczukowicz.gitlab.client.ratelimit;
    exports io.github.piotr_janczukowicz.gitlab.json;
    exports io.github.piotr_janczukowicz.gitlab.pagination;

    opens io.github.piotr_janczukowicz.gitlab.client to
            tools.jackson.databind,
            common.core;
    opens io.github.piotr_janczukowicz.gitlab.client.pagination to
            tools.jackson.databind,
            common.core;
    opens io.github.piotr_janczukowicz.gitlab.json to
            tools.jackson.databind,
            common.core;

    uses io.github.piotr_janczukowicz.gitlab.json.GitLabJsonModule;
}
