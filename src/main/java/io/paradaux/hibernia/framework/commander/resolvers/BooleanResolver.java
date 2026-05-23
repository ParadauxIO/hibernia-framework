package io.paradaux.hibernia.framework.commander.resolvers;

import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses common truthy/falsy tokens into {@link Boolean}.
 *
 * <p>Accepts (case-insensitive): {@code true} / {@code false}, {@code yes} /
 * {@code no}, {@code y} / {@code n}, {@code 1} / {@code 0}, {@code on} /
 * {@code off}. Other inputs return {@link Optional#empty()}.
 *
 * <p>Tab completion deliberately surfaces only {@code true} / {@code false}
 * — the canonical pair — to avoid steering users toward shorthand that
 * isn't easy to skim in a chat command.
 */
public class BooleanResolver implements ParameterResolver<Boolean> {

    private static final List<String> SUGGESTIONS = List.of("true", "false");

    @Override
    public Class<Boolean> type() {
        return Boolean.class;
    }

    @Override
    public Optional<Boolean> resolve(String token, CommandSender sender) {
        if (token == null) return Optional.empty();
        switch (token.toLowerCase(Locale.ROOT).trim()) {
            case "true":
            case "yes":
            case "y":
            case "1":
            case "on":
                return Optional.of(Boolean.TRUE);
            case "false":
            case "no":
            case "n":
            case "0":
            case "off":
                return Optional.of(Boolean.FALSE);
            default:
                return Optional.empty();
        }
    }

    @Override
    public List<String> suggestions(String prefix, CommandSender sender) {
        if (prefix == null || prefix.isEmpty()) return SUGGESTIONS;
        String p = prefix.toLowerCase(Locale.ROOT);
        return SUGGESTIONS.stream().filter(s -> s.startsWith(p)).toList();
    }
}
