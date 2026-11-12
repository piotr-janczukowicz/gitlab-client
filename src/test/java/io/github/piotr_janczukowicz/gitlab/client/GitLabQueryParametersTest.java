package io.github.piotr_janczukowicz.gitlab.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabQueryParametersTest {

    enum Order {
        CREATED("created_at");

        private final String wire;

        Order(final String wire) {
            this.wire = wire;
        }

        String wireValue() {
            return wire;
        }
    }

    @Test
    void build_shouldKeepTheOrderOfTheCalls_whenSeveralKindsAreAdded() {
        final GitLabQueryParameters parameters = GitLabQueryParameters.builder();

        final Map<String, String> query = parameters
                .text("state", "opened")
                .value("confidential", true)
                .value("created_after", Instant.parse("2026-01-01T00:00:00Z"))
                .wire("order_by", Order.CREATED, Order::wireValue)
                .build();

        assertThat(query)
                .containsExactly(
                        Map.entry("state", "opened"),
                        Map.entry("confidential", "true"),
                        Map.entry("created_after", "2026-01-01T00:00:00Z"),
                        Map.entry("order_by", "created_at"));
    }

    @Test
    void build_shouldLeaveOutEveryNullValue_whenNothingIsSet() {
        final GitLabQueryParameters parameters = GitLabQueryParameters.builder();
        final Order noOrder = null;
        final List<Order> noOrders = null;

        final Map<String, String> query = parameters
                .text("a", null)
                .value("b", null)
                .wire("c", noOrder, Order::wireValue)
                .list("d", null)
                .wireList("e", noOrders, Order::wireValue)
                .map("f", null)
                .build();

        assertThat(query).isEmpty();
    }

    @Test
    void build_shouldJoinListsWithCommas_whenListsAreAdded() {
        final GitLabQueryParameters parameters = GitLabQueryParameters.builder();

        final Map<String, String> query = parameters
                .list("labels", List.of("a", "b"))
                .list("ids", List.of(1L, 2L))
                .wireList("order", List.of(Order.CREATED, Order.CREATED), Order::wireValue)
                .build();

        assertThat(query)
                .containsExactly(
                        Map.entry("labels", "a,b"),
                        Map.entry("ids", "1,2"),
                        Map.entry("order", "created_at,created_at"));
    }

    @Test
    void build_shouldWriteNameBracketKey_whenAMapIsAdded() {
        final Map<String, Object> values = new LinkedHashMap<>();
        values.put("a", 1);
        values.put("b", "x");
        final GitLabQueryParameters parameters = GitLabQueryParameters.builder();

        final Map<String, String> query =
                parameters.map("custom_attributes", values).build();

        assertThat(query)
                .containsExactly(Map.entry("custom_attributes[a]", "1"), Map.entry("custom_attributes[b]", "x"));
    }

    @Test
    void all_shouldAddAndReplace_whenGivenEntries() {
        final GitLabQueryParameters parameters = GitLabQueryParameters.builder().text("a", "1");

        final Map<String, String> query = parameters.all(Map.of("a", "2")).build();

        assertThat(query).containsExactly(Map.entry("a", "2"));
    }

    @Test
    void all_shouldRejectNull_whenParametersAreMissing() {
        final GitLabQueryParameters parameters = GitLabQueryParameters.builder();

        final Throwable thrown = catchThrowable(() -> parameters.all(null));

        assertThat(thrown).isInstanceOf(NullPointerException.class);
    }

    @Test
    void build_shouldReturnAnUnmodifiableMap_whenCalled() {
        final Map<String, String> query =
                GitLabQueryParameters.builder().text("a", "1").build();

        final Throwable thrown = catchThrowable(() -> query.put("b", "2"));

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }
}
