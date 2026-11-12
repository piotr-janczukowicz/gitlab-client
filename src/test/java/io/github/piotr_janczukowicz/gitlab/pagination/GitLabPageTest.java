package io.github.piotr_janczukowicz.gitlab.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import io.github.piotr_janczukowicz.testsupport.UnitTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

@UnitTest
class GitLabPageTest {

    private final List<String> checked = new ArrayList<>();

    @Test
    void items_shouldBeACopy_whenTheSourceListChangesLater() {
        final List<String> source = new ArrayList<>(List.of("a"));
        final GitLabPage<String> page = new GitLabPage<>(source, walk(unused -> Optional.empty()), 1, null);

        source.add("b");
        final Throwable thrown = catchThrowable(() -> page.items().add("c"));

        assertSoftly(softly -> {
            softly.assertThat(page.items()).containsExactly("a");
            softly.assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
        });
    }

    @Test
    void accessors_shouldReturnWhatTheWalkGave_whenThePageIsBuilt() {
        final GitLabPage<String> page = new GitLabPage<>(List.of("a"), walk(unused -> Optional.empty()), 4, 99);

        assertSoftly(softly -> {
            softly.assertThat(page.pageCount()).isEqualTo(4);
            softly.assertThat(page.totalCount()).isEqualTo(99);
        });
    }

    @Test
    void constructor_shouldRefuse_whenThePageCountIsBelowOne() {
        final Throwable thrown =
                catchThrowable(() -> new GitLabPage<>(List.of(), walk(unused -> Optional.empty()), 0, null));

        assertThat(thrown).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pageCount");
    }

    @Test
    void constructor_shouldRefuse_whenAnArgumentIsNull() {
        final Throwable noItems =
                catchThrowable(() -> new GitLabPage<>(null, walk(unused -> Optional.empty()), 1, null));
        final Throwable noWalk = catchThrowable(() -> new GitLabPage<>(List.of(), null, 1, null));

        assertSoftly(softly -> {
            softly.assertThat(noItems).isInstanceOf(NullPointerException.class);
            softly.assertThat(noWalk).isInstanceOf(NullPointerException.class);
        });
    }

    @Test
    void next_shouldCheckThatThePageIsTheLatestAndLoadTheFollowingOne_whenAskedForTheNextPage() throws IOException {
        final GitLabPage<String> second = new GitLabPage<>(List.of("b"), walk(unused -> Optional.empty()), 2, null);
        final GitLabPage<String> first = new GitLabPage<>(List.of("a"), walk(unused -> Optional.of(second)), 1, null);

        final Optional<GitLabPage<String>> next = first.next();

        assertSoftly(softly -> {
            softly.assertThat(next).containsSame(second);
            softly.assertThat(checked).containsExactly("a");
        });
    }

    @Test
    void all_shouldCollectTheItemsOfEveryPage_whenPagesFollowEachOther() throws IOException {
        final GitLabPage<String> third = new GitLabPage<>(List.of("e"), walk(unused -> Optional.empty()), 3, null);
        final GitLabPage<String> second =
                new GitLabPage<>(List.of("c", "d"), walk(unused -> Optional.of(third)), 2, null);
        final GitLabPage<String> first =
                new GitLabPage<>(List.of("a", "b"), walk(unused -> Optional.of(second)), 1, null);

        final List<String> items = first.all();

        assertThat(items).containsExactly("a", "b", "c", "d", "e");
    }

    @Test
    void stream_shouldReadItemsLazily_whenTheCallerStopsEarly() {
        final List<String> loaded = new ArrayList<>();
        final GitLabPage<String> second = new GitLabPage<>(List.of("c"), walk(unused -> Optional.empty()), 2, null);
        final GitLabPage<String> first = new GitLabPage<>(
                List.of("a", "b"),
                walk(unused -> {
                    loaded.add("second");
                    return Optional.of(second);
                }),
                1,
                null);

        final List<String> items = first.stream().limit(2).toList();

        assertSoftly(softly -> {
            softly.assertThat(items).containsExactly("a", "b");
            softly.assertThat(loaded).isEmpty();
        });
    }

    @Test
    void pages_shouldReturnEveryPageAsAGitLabPage_whenPagesFollowEachOther() {
        final GitLabPage<String> second = new GitLabPage<>(List.of("b"), walk(unused -> Optional.empty()), 2, 7);
        final GitLabPage<String> first = new GitLabPage<>(List.of("a"), walk(unused -> Optional.of(second)), 1, 7);

        final List<GitLabPage<String>> pages = first.pages().toList();

        assertThat(pages).containsExactly(first, second);
    }

    @Test
    void forEachPage_shouldVisitEveryPageInOrder_whenPagesFollowEachOther() throws IOException {
        final GitLabPage<String> second = new GitLabPage<>(List.of("b"), walk(unused -> Optional.empty()), 2, null);
        final GitLabPage<String> first = new GitLabPage<>(List.of("a"), walk(unused -> Optional.of(second)), 1, null);
        final List<Integer> counts = new ArrayList<>();

        first.forEachPage(page -> counts.add(page.pageCount()));

        assertThat(counts).containsExactly(1, 2);
    }

    private GitLabPageWalk<String> walk(final Function<GitLabPage<String>, Optional<GitLabPage<String>>> loader) {
        return new GitLabPageWalk<>() {
            @Override
            public Optional<GitLabPage<String>> loadNext(final GitLabPage<String> current) {
                return loader.apply(current);
            }

            @Override
            public void requireLatest(final GitLabPage<String> page) {
                checked.add(page.items().getFirst());
            }
        };
    }
}
