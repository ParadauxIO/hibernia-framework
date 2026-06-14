package io.paradaux.hibernia.framework.usher;

import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.Objects;

/**
 * A piece of dialog text: either a {@link Message} key (with optional placeholder values) resolved at
 * render time through the bound {@code Message} bean, or a literal Adventure {@link Component}.
 *
 * <p>Keeping text as a key-or-component value (rather than an eagerly built {@code Component}) lets the
 * spec stay renderer-agnostic and lets operators re-word/translate dialog labels through
 * {@code messages.properties}, exactly like command feedback.</p>
 */
public sealed interface Text permits Text.Keyed, Text.Literal {

    /** A message key with optional placeholder pairs ({@code "key", value, "key2", value2, ...}). */
    static Text key(String key, Object... placeholders) {
        return new Keyed(Objects.requireNonNull(key, "key"), toMap(placeholders));
    }

    /** A literal component, used verbatim (no message lookup). */
    static Text of(Component component) {
        return new Literal(Objects.requireNonNull(component, "component"));
    }

    record Keyed(String key, Map<String, Object> placeholders) implements Text {}

    record Literal(Component component) implements Text {}

    private static Map<String, Object> toMap(Object... pairs) {
        if (pairs.length == 0) return Map.of();
        if ((pairs.length & 1) == 1) {
            throw new IllegalArgumentException("Placeholder arguments must be in pairs: key, value, …");
        }
        // LinkedHashMap to preserve declaration order; small by construction.
        Map<String, Object> map = new java.util.LinkedHashMap<>(pairs.length / 2);
        for (int i = 0; i < pairs.length; i += 2) {
            if (!(pairs[i] instanceof String name)) {
                throw new IllegalArgumentException("Placeholder name at index " + i + " must be a String");
            }
            map.put(name, pairs[i + 1]);
        }
        return map;
    }
}
