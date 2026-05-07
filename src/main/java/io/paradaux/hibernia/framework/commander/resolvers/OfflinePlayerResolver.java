package io.paradaux.hibernia.framework.commander.resolvers;

import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class OfflinePlayerResolver implements ParameterResolver<OfflinePlayer> {
    public Class<OfflinePlayer> type() {
        return OfflinePlayer.class;
    }

    public Optional<OfflinePlayer> resolve(String token, CommandSender sender) {
        // Always return a non-null OfflinePlayer so the handler can decide
        // how to render an unknown-player rejection (typically a check on
        // hasPlayedBefore() with a plugin-specific i18n message). Returning
        // empty here would short-circuit dispatch with the framework's
        // generic "Invalid target: X" before the handler runs, which makes
        // plugin-side null/has-played-before checks unreachable.
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(token);
        if (cached != null) return Optional.of(cached);
        @SuppressWarnings("deprecation") // synthetic OfflinePlayer for a never-joined name; safe on offline-mode servers
        OfflinePlayer synthetic = Bukkit.getOfflinePlayer(token);
        return Optional.of(synthetic);
    }

    public List<String> suggestions(String prefix, CommandSender sender) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).limit(20).toList();
    }
}
