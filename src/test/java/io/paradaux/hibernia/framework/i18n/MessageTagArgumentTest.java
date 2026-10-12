package io.paradaux.hibernia.framework.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Placeholders inside a tag's arguments — {@code <click:open_url:'{url}'>} — must be
 * substituted with the value itself. MiniMessage does not resolve tags inside another
 * tag's arguments, so the generated {@code <phN>} tag used everywhere else would be
 * left there literally (an unusable URL / command / clipboard payload).
 */
class MessageTagArgumentTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @TempDir
    Path tempDir;

    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        doAnswer(inv -> null).when(plugin).saveResource(anyString(), anyBoolean());
        when(plugin.getResource(anyString())).thenReturn((InputStream) null);
    }

    private Message messages(String content) throws Exception {
        Files.writeString(tempDir.resolve("messages.properties"), content, StandardCharsets.UTF_8);
        return new Message(plugin);
    }

    private static List<ClickEvent> clicks(Component root) {
        List<ClickEvent> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(Component c, List<ClickEvent> out) {
        if (c.clickEvent() != null && !out.contains(c.clickEvent())) {
            out.add(c.clickEvent());
        }
        c.children().forEach(child -> collect(child, out));
    }

    @Test
    void openUrl_argumentReceivesTheValue() throws Exception {
        Message message = messages("""
                export=Exported: <click:open_url:'{url}'>{url}</click>
                """);

        Component out = message.component("export", "url", "https://pastes.example/abc");

        assertEquals("Exported: https://pastes.example/abc", PLAIN.serialize(out));
        assertEquals(List.of(ClickEvent.openUrl("https://pastes.example/abc")), clicks(out));
    }

    @Test
    void runCommand_argumentMixesLiteralTextAndValue() throws Exception {
        Message message = messages("""
                line=<click:run_command:'/db info {firm}'>{firm}</click>
                """);

        Component out = message.component("line", "firm", "Acme");

        assertEquals("Acme", PLAIN.serialize(out));
        assertEquals(List.of(ClickEvent.runCommand("/db info Acme")), clicks(out));
    }

    @Test
    void copyToClipboard_keepsTheTextAfterTheTag() throws Exception {
        Message message = messages("""
                issued=Key issued.\\nToken: <click:copy_to_clipboard:'{token}'><yellow>{token}</yellow></click>
                """);

        Component out = message.component("issued", "token", "eyJhbGciOi.payload.sig");

        assertEquals("Key issued.\nToken: eyJhbGciOi.payload.sig", PLAIN.serialize(out));
        assertEquals(List.of(ClickEvent.copyToClipboard("eyJhbGciOi.payload.sig")), clicks(out));
    }

    @Test
    void argumentValue_cannotBreakOutOfTheQuotes() throws Exception {
        Message message = messages("""
                line=<click:run_command:'/db info {firm}'>{firm}</click> done
                """);

        Component out = message.component("line", "firm", "A'><red>x");

        assertEquals("A'><red>x done", PLAIN.serialize(out));
        assertEquals(List.of(ClickEvent.runCommand("/db info A'><red>x")), clicks(out));
    }

    @Test
    void proseThatOnlyLooksLikeATag_stillEscapesTheValue() throws Exception {
        Message message = messages("""
                line=a < b, it's {x}
                """);

        Component out = message.component("line", "x", "<red>boo</red>");

        assertEquals("a < b, it's <red>boo</red>", PLAIN.serialize(out));
    }

    @Test
    void realWorldTokenReceipt_rendersBothLines() throws Exception {
        Message message = messages("""
                placeholder.prefix=<bold><blue>TREASURY</blue></bold><gray>»</gray>
                placeholder.secbegin=<#6f6fff>
                placeholder.secend=</#6f6fff>
                issue={prefix} Personal API key {secbegin}#{keyId}{secend} issued for account {secbegin}{accountId}{secend}.\\n<gray>Token (click to copy):</gray> <click:copy_to_clipboard:'{token}'>{secbegin}{token}{secend}</click>
                """);

        Component out = message.component("issue", "keyId", "30", "accountId", "62", "token", "eyJhbGciOi.payload.sig");

        assertEquals("TREASURY» Personal API key #30 issued for account 62.\nToken (click to copy): eyJhbGciOi.payload.sig",
                PLAIN.serialize(out));
    }

    @Test
    void palettePlaceholder_inArgument_isExpanded() throws Exception {
        Message message = messages("""
                placeholder.site=https://example.org
                link=<click:open_url:'{site}/docs'>docs</click>
                """);

        Component out = message.component("link");

        assertEquals(List.of(ClickEvent.openUrl("https://example.org/docs")), clicks(out));
    }
}
