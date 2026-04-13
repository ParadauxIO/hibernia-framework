package io.paradaux.hibernia.framework.commander.resolvers;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OfflinePlayerResolverTest {

    private final OfflinePlayerResolver resolver = new OfflinePlayerResolver();
    private final CommandSender sender = mock(CommandSender.class);

    @Test
    void type_returnsOfflinePlayerClass() {
        assertEquals(OfflinePlayer.class, resolver.type());
    }

    @Test
    void resolve_returnsCachedOfflinePlayer() throws Exception {
        OfflinePlayer offlinePlayer = mock(OfflinePlayer.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayerIfCached("Alex")).thenReturn(offlinePlayer);

            Optional<OfflinePlayer> out = resolver.resolve("Alex", sender);

            assertTrue(out.isPresent());
            assertEquals(offlinePlayer, out.get());
        }
    }

    @Test
    void suggestions_filtersByPrefix_caseInsensitive_andLimitsTo20() {
        List<Player> players = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> {
                    Player p = mock(Player.class);
                    when(p.getName()).thenReturn(i < 25 ? "Alpha" + i : "Beta" + i);
                    return p;
                })
                .toList();

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.copyOf(players));

            List<String> out = resolver.suggestions("aLp", sender);

            assertTrue(out.stream().allMatch(n -> n.toLowerCase().startsWith("alp")));
            assertTrue(out.size() <= 20);
        }
    }
}
