package io.paradaux.hibernia.framework.i18n;

import net.kyori.adventure.text.Component;
import io.paradaux.hibernia.framework.models.HiberniaPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageTest {

    @TempDir
    Path tempDir;

    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
    }

    @Test
    void format_expandsGlobalAndNamespacePlaceholders() throws Exception {
        writeMessages("""
                placeholder.color=<red>
                placeholder.prefix={color}[Hib]
                cmd.placeholder.warn={prefix} WARN
                cmd.greet=Hello {name} {warn}
                """);
        stubSaveResourceNoop();

        Message message = new Message(plugin);

        String out = message.format("cmd.greet", Map.of("name", "Alex"));

        assertEquals("Hello Alex <red>[Hib] WARN", out);
    }

    @Test
    void format_restoresEscapedBraces() throws Exception {
        writeMessages("literal=Use {{braces}} and {name}");
        stubSaveResourceNoop();

        Message message = new Message(plugin);

        String out = message.format("literal", Map.of("name", "value"));

        assertEquals("Use {braces} and value", out);
    }

    @Test
    void format_kvPairsValidation_throwsForOddOrNonStringKey() throws Exception {
        writeMessages("k=Hello {name}");
        stubSaveResourceNoop();

        Message message = new Message(plugin);

        assertThrows(IllegalArgumentException.class, () -> message.format("k", "name"));
        assertThrows(IllegalArgumentException.class, () -> message.format("k", 1, "value"));
    }

    @Test
    void component_and_sendCommandSender_work() throws Exception {
        writeMessages("chat=<green>Hello {name}");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        Component component = message.component("chat", "name", "Sam");
        CommandSender sender = mock(CommandSender.class);
        message.send(sender, "chat", "name", "Sam");

        assertNotNull(component);
        verify(sender).sendMessage(any(Component.class));
    }

    @Test
    void send_hiberniaPlayerAndUuid_routesThroughBukkitPlayerLookup() throws Exception {
        writeMessages("chat=Hello {name}");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        Player player = mock(Player.class);
        HiberniaPlayer hp = mock(HiberniaPlayer.class);
        UUID uuid = UUID.randomUUID();

        when(hp.getCurrentName()).thenReturn("Alex");

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer("Alex")).thenReturn(player);
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);

            message.send(hp, "chat", "name", "Sam");
            message.send(uuid, "chat", "name", "Sam");

            verify(player, org.mockito.Mockito.times(2)).sendMessage(any(Component.class));
        }
    }

    @Test
    void send_collection_sendsToAllRecipients() throws Exception {
        writeMessages("chat=Hello");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        CommandSender a = mock(CommandSender.class);
        CommandSender b = mock(CommandSender.class);

        message.send(List.of(a, b), "chat");

        verify(a).sendMessage(any(Component.class));
        verify(b).sendMessage(any(Component.class));
    }

    @Test
    void broadcast_sendsToOnlinePlayersAndConsole() throws Exception {
        writeMessages("chat=Broadcast!");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        Player p1 = mock(Player.class);
        Player p2 = mock(Player.class);
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Set.of(p1, p2));
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);

            message.broadcast("chat");

            verify(p1).sendMessage(any(Component.class));
            verify(p2).sendMessage(any(Component.class));
            verify(console).sendMessage(any(Component.class));
        }
    }

    @Test
    void reload_readsUpdatedMessagesFile() throws Exception {
        writeMessages("chat=Before");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        assertEquals("Before", message.format("chat"));

        writeMessages("chat=After");
        message.reload();

        assertEquals("After", message.format("chat"));
    }

    private void stubSaveResourceNoop() {
        doAnswer(invocation -> null).when(plugin).saveResource(anyString(), anyBoolean());
    }

    private void writeMessages(String content) throws IOException {
        Files.writeString(tempDir.resolve("messages.properties"), content, StandardCharsets.UTF_8);
    }
}
