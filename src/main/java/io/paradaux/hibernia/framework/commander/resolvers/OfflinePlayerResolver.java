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
        // Resolve from the local player cache ONLY. We deliberately do NOT fall back
        // to Bukkit.getOfflinePlayer(String): on an online-mode server that blocks on
        // a Mojang lookup, which FAILS for Bedrock/Floodgate players (their
        // '.'-prefixed names aren't in Mojang's DB) and fabricates a bogus UUID that
        // is NOT the player's real Floodgate UUID — a command acting on it writes
        // ghost rows (e.g. employing someone who never actually gets employed).
        //
        // An uncached name resolves to empty, so the framework rejects it as an
        // unknown target and no handler ever acts on a fabricated UUID. A Bedrock (or
        // Java) player who has joined before is in the cache under their real name and
        // resolves correctly.
        return Optional.ofNullable(Bukkit.getOfflinePlayerIfCached(token));
    }

    public List<String> suggestions(String prefix, CommandSender sender) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).limit(20).toList();
    }
}
