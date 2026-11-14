package io.github.piotr_janczukowicz.gitlab.client;

import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNoNullElements;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNonNull;
import static io.github.piotr_janczukowicz.util.ValidationUtils.requireNotBlank;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Builds the query parameters of a generated Filter or call: a value that is {@code null} is left out, a list is joined
 * with commas, and a map becomes {@code name[key]=value}. Keeping the rules here keeps every generated Filter to one
 * expression.
 */
public final class GitLabQueryParameters {

    private static final String NAME = "name";

    private final Map<String, String> query = new LinkedHashMap<>();

    private GitLabQueryParameters() {}

    /**
     * Starts an empty set of parameters.
     *
     * @return the builder
     */
    public static GitLabQueryParameters builder() {
        return new GitLabQueryParameters();
    }

    /**
     * Adds every entry of {@code parameters}.
     *
     * @param parameters the entries to add; an entry of the same name is replaced
     * @return this builder
     * @throws NullPointerException if {@code parameters} is {@code null}
     */
    public GitLabQueryParameters all(final Map<String, String> parameters) {
        query.putAll(requireNonNull(parameters, "parameters"));
        return this;
    }

    /**
     * Adds a text value.
     *
     * @param name the parameter name
     * @param value the value, or {@code null} to leave it out
     * @return this builder
     */
    public GitLabQueryParameters text(final String name, final @Nullable String value) {
        requireNotBlank(name, NAME);
        if (value != null) {
            query.put(name, value);
        }
        return this;
    }

    /**
     * Adds a number, a boolean, a date or an instant with its {@code String.valueOf}.
     *
     * @param name the parameter name
     * @param value the value, or {@code null} to leave it out
     * @return this builder
     */
    public GitLabQueryParameters value(final String name, final @Nullable Object value) {
        requireNotBlank(name, NAME);
        if (value != null) {
            query.put(name, String.valueOf(value));
        }
        return this;
    }

    /**
     * Adds an enum by its wire value.
     *
     * @param name the parameter name
     * @param value the constant, or {@code null} to leave it out
     * @param wireValue gives the wire value of a constant
     * @param <E> the enum type
     * @return this builder
     */
    public <E> GitLabQueryParameters wire(final String name, final @Nullable E value, final Function<E, ?> wireValue) {
        requireNotBlank(name, NAME);
        if (value != null) {
            query.put(name, String.valueOf(requireNonNull(wireValue.apply(value), name + " wire value")));
        }
        return this;
    }

    /**
     * Adds a list, its items joined with commas.
     *
     * @param name the parameter name
     * @param values the items, or {@code null} to leave the parameter out
     * @return this builder
     */
    public GitLabQueryParameters list(final String name, final @Nullable List<?> values) {
        requireNotBlank(name, NAME);
        if (values != null) {
            requireNoNullElements(values, name);
            query.put(name, values.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        return this;
    }

    /**
     * Adds a list of enum constants, their wire values joined with commas.
     *
     * @param name the parameter name
     * @param values the constants, or {@code null} to leave the parameter out
     * @param wireValue gives the wire value of a constant
     * @param <E> the enum type
     * @return this builder
     */
    public <E> GitLabQueryParameters wireList(
            final String name, final @Nullable List<E> values, final Function<E, ?> wireValue) {
        requireNotBlank(name, NAME);
        if (values != null) {
            requireNoNullElements(values, name);
            query.put(
                    name,
                    values.stream()
                            .map(item -> requireNonNull(wireValue.apply(item), name + " wire value"))
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")));
        }
        return this;
    }

    /**
     * Adds a hash as {@code name[key]=value}, one parameter per entry.
     *
     * @param name the parameter name
     * @param values the entries, or {@code null} to leave the parameter out; an entry whose value is {@code null} is
     *     left out
     * @return this builder
     * @throws NullPointerException if a key is {@code null}
     */
    public GitLabQueryParameters map(final String name, final @Nullable Map<String, ?> values) {
        requireNotBlank(name, NAME);
        if (values != null) {
            values.forEach((key, item) -> {
                requireNonNull(key, name + " key");
                if (item != null) {
                    query.put(name + "[" + key + "]", String.valueOf(item));
                }
            });
        }
        return this;
    }

    /**
     * The parameters, in the order they were added.
     *
     * @return an unmodifiable map
     */
    public Map<String, String> build() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(query));
    }
}
