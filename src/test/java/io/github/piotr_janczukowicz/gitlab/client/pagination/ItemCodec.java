package io.github.piotr_janczukowicz.gitlab.client.pagination;

import io.github.piotr_janczukowicz.json.JsonCodec;
import io.github.piotr_janczukowicz.json.JsonDecodeException;
import io.github.piotr_janczukowicz.json.JsonType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A test double of the codec for the pager tests: it reads {@code "id":N} pairs of a page body into {@link Item}s. */
final class ItemCodec implements JsonCodec {

    record Item(long id) {}

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*([^,}]+)");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    @Override
    public <T> T decode(final byte[] json, final Class<T> type) {
        throw new UnsupportedOperationException("the pager reads lists through a JsonType");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T decode(final byte[] json, final JsonType<T> type) {
        final Matcher matcher = ID.matcher(new String(json, StandardCharsets.UTF_8));
        final List<Item> items = new ArrayList<>();
        while (matcher.find()) {
            final String value = matcher.group(1).strip();
            if (!NUMBER.matcher(value).matches()) {
                throw new JsonDecodeException("id is not a number: " + value, null);
            }
            items.add(new Item(Long.parseLong(value)));
        }
        return (T) List.copyOf(items);
    }

    @Override
    public byte[] encode(final Object value) {
        throw new UnsupportedOperationException("the pager tests never encode");
    }
}
