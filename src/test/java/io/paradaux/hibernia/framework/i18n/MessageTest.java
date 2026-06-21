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
    void send_hiberniaPlayerAndUuid_resolveByUuid() throws Exception {
        writeMessages("chat=Hello {name}");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        Player player = mock(Player.class);
        HiberniaPlayer hp = mock(HiberniaPlayer.class);
        UUID uuid = UUID.randomUUID();

        // HiberniaPlayer routing must use the stable UUID, not the (possibly
        // stale) current name.
        when(hp.getUniqueId()).thenReturn(uuid);

        try (MockedStatic<Bukkit> bukkit = org.mockito.Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);

            message.send(hp, "chat", "name", "Sam");
            message.send(uuid, "chat", "name", "Sam");

            verify(player, org.mockito.Mockito.times(2)).sendMessage(any(Component.class));
        }
    }

    @Test
    void format_escapesMiniMessageTagsInUserValues() throws Exception {
        writeMessages("chat=Hello {name}");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        Component out = message.component("chat", "name", "<red>Hacker</red>");

        // The tags must render literally, not as markup.
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(out);
        assertEquals("Hello <red>Hacker</red>", plain);
    }

    @Test
    void format_userValuesCannotExpandPlaceholders() throws Exception {
        writeMessages("""
                placeholder.prefix=[SECRET]
                chat=Hello {name}
                """);
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        String out = message.format("chat", Map.of("name", "{prefix}"));

        // A player-controlled value containing {prefix} must stay literal.
        assertEquals("Hello {prefix}", out);
    }

    @Test
    void format_richValuesPassMarkupThrough() throws Exception {
        writeMessages("chat=Hello {name}");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        Component out = message.component("chat", Map.of("name", Message.rich("<red>Trusted</red>")));

        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(out);
        // Markup parsed: tags do not appear in the plain text.
        assertEquals("Hello Trusted", plain);
    }

    @Test
    void component_rendersComponentValuedPlaceholder_preservingFormatting() throws Exception {
        writeMessages("bought=<gray>You bought {item}");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        Component item = Component.text("Diamond Sword")
                .color(net.kyori.adventure.text.format.NamedTextColor.RED);

        Component out = message.component("bought", "item", item);

        // The Component was inserted (not toString()'d): its text and colour survive.
        assertEquals("You bought Diamond Sword", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(out));
        String mmOut = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(out);
        assertTrue(mmOut.contains("Diamond Sword"));
        assertTrue(mmOut.contains("red"));   // the placeholder's own colour, not toString garbage
    }

    @Test
    void component_nestedPalettePlaceholdersResolveRecursively() throws Exception {
        writeMessages("""
                placeholder.brand=<bold>{label}</bold>
                placeholder.label=ACME
                line={brand} Store
                """);
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        Component out = message.component("line");

        assertEquals("ACME Store", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(out));
    }

    @Test
    void component_callerValueOverridesPalettePlaceholder() throws Exception {
        writeMessages("""
                placeholder.name=DefaultName
                greet=Hi {name}
                """);
        stubSaveResourceNoop();

        Message message = new Message(plugin);

        assertEquals("Hi DefaultName", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(message.component("greet")));
        assertEquals("Hi Alex", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(message.component("greet", "name", "Alex")));
    }

    @Test
    void papi_resolvesOperatorTokens_butNotCallerValues() throws Exception {
        writeMessages("line=%greeting% {name}");
        stubSaveResourceNoop();

        Message message = new Message(plugin)
                .placeholders((player, text) -> text.replace("%greeting%", "Hello"));

        // The %token% in the operator pattern resolves; the same token passed as a caller value stays
        // literal (caller values are inert — PAPI never widens the injection surface for player input).
        Component out = message.component("line", "name", "%greeting%");

        assertEquals("Hello %greeting%", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(out));
    }

    @Test
    void papi_resolvesInsidePaletteEntries() throws Exception {
        writeMessages("""
                placeholder.brand=%server%
                line={brand} Store
                """);
        stubSaveResourceNoop();

        Message message = new Message(plugin)
                .placeholders((player, text) -> text.replace("%server%", "MyServer"));

        assertEquals("MyServer Store", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(message.component("line")));
    }

    @Test
    void placeholderApiSupport_noopsWhenPlaceholderApiAbsent() {
        PapiSupport papi = new PlaceholderApiSupport();   // PlaceholderAPI is not on the test classpath
        assertEquals("%player_name%", papi.resolve(null, "%player_name%"));
        assertEquals("no tokens here", papi.resolve(null, "no tokens here"));
    }

    @Test
    void componentOr_usesKeyWhenPresentAndFallbackOtherwise() throws Exception {
        writeMessages("hibernia.error.not-found=<red>Missing: {message}");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        var serializer = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();

        Component fromKey = message.componentOr("hibernia.error.not-found", "<red>{message}</red>", "message", "thing");
        assertEquals("Missing: thing", serializer.serialize(fromKey));

        Component fromFallback = message.componentOr("hibernia.error.conflict", "<red>{message}</red>", "message", "clash");
        assertEquals("clash", serializer.serialize(fromFallback));
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

    // ── per-locale bundles ───────────────────────────────────────────────────

    @Test
    void format_selectsLocaleBundle_andFallsBackPerKey() throws Exception {
        writeMessages("""
                greeting=Hello {name}
                only.base=Base only
                """);
        writeBundle("ga", """
                greeting=Dia duit {name}
                """);   // note: 'only.base' is NOT translated
        stubSaveResourceNoop();

        Message message = new Message(plugin);

        // locale-specific key
        assertEquals("Dia duit Alex", message.format(java.util.Locale.of("ga"), "greeting", "name", "Alex"));
        // base key
        assertEquals("Hello Alex", message.format(java.util.Locale.ENGLISH, "greeting", "name", "Alex"));
        // per-key fallback: missing in ga → base text
        assertEquals("Base only", message.format(java.util.Locale.of("ga"), "only.base"));
    }

    @Test
    void format_countryFallsBackToLanguageThenBase() throws Exception {
        writeMessages("k=base");
        writeBundle("pt", "k=portugues");
        stubSaveResourceNoop();

        Message message = new Message(plugin);

        // pt_BR has no bundle → falls back to pt
        assertEquals("portugues", message.format(java.util.Locale.of("pt", "BR"), "k"));
        // fr has no bundle → base
        assertEquals("base", message.format(java.util.Locale.of("fr"), "k"));
    }

    @Test
    void send_rendersInPlayerLocale() throws Exception {
        writeMessages("greeting=Hello");
        writeBundle("ga", "greeting=Dia duit");
        stubSaveResourceNoop();

        Message message = new Message(plugin);
        Player player = mock(Player.class);
        when(player.locale()).thenReturn(java.util.Locale.of("ga"));

        message.send(player, "greeting");

        org.mockito.ArgumentCaptor<Component> captor = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(captor.capture());
        assertEquals("Dia duit", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(captor.getValue()));
    }

    @Test
    void availableLocales_includesBaseAndTranslations() throws Exception {
        writeMessages("k=v");
        writeBundle("ga", "k=v");
        writeBundle("pt_BR", "k=v");
        stubSaveResourceNoop();

        Message message = new Message(plugin);

        assertTrue(message.availableLocales().contains(java.util.Locale.ROOT));
        assertTrue(message.availableLocales().contains(java.util.Locale.of("ga")));
        assertTrue(message.availableLocales().contains(java.util.Locale.of("pt", "BR")));
    }

    @Test
    void defaultLocale_appliesToNonPlayerSenders() throws Exception {
        writeMessages("k=base");
        writeBundle("ga", "k=as Gaeilge");
        stubSaveResourceNoop();

        Message message = new Message(plugin).defaultLocale(java.util.Locale.of("ga"));

        assertEquals("as Gaeilge", message.format("k"));   // no-locale overload uses the default
    }

    private void stubSaveResourceNoop() {
        doAnswer(invocation -> null).when(plugin).saveResource(anyString(), anyBoolean());
    }

    private void writeMessages(String content) throws IOException {
        Files.writeString(tempDir.resolve("messages.properties"), content, StandardCharsets.UTF_8);
    }

    private void writeBundle(String localeSuffix, String content) throws IOException {
        Files.writeString(tempDir.resolve("messages_" + localeSuffix + ".properties"), content, StandardCharsets.UTF_8);
    }
}
