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
        return Optional.ofNullable(Bukkit.getOfflinePlayerIfCached(token));
    }

    public List<String> suggestions(String prefix, CommandSender sender) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).limit(20).toList();
    }
}
