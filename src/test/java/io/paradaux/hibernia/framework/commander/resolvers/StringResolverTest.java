package io.paradaux.hibernia.framework.commander.resolvers;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StringResolverTest {

    private StringResolver resolver;
    private CommandSender sender;

    @BeforeEach
    void setUp() {
        resolver = new StringResolver();
        sender = Mockito.mock(CommandSender.class);
    }

    @Test
    void type_returnsStringClass() {
        assertEquals(String.class, resolver.type());
    }

    @Test
    void resolve_cleanToken_returnsSanitizedValue() throws Exception {
        Optional<String> result = resolver.resolve("hello", sender);
        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    @Test
    void resolve_nullToken_returnsEmpty() throws Exception {
        Optional<String> result = resolver.resolve(null, sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_blankToken_returnsEmpty() throws Exception {
        Optional<String> result = resolver.resolve("   ", sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_emptyToken_returnsEmpty() throws Exception {
        Optional<String> result = resolver.resolve("", sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_tokenWithMiniMessageTags_tagsStripped() throws Exception {
        Optional<String> result = resolver.resolve("<red>hello</red>", sender);
        assertTrue(result.isPresent());
        assertEquals("hello", result.get());
    }

    @Test
    void resolve_tokenThatBecomesBlankAfterSanitize_returnsEmpty() throws Exception {
        // A token containing only special chars that sanitize() strips
        Optional<String> result = resolver.resolve("!@#$%", sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_tokenWithSpecialChars_specialCharsStripped() throws Exception {
        Optional<String> result = resolver.resolve("hello!world", sender);
        assertTrue(result.isPresent());
        assertEquals("helloworld", result.get());
    }

    @Test
    void resolve_tokenWithUnderscores_underscoresPreserved() throws Exception {
        Optional<String> result = resolver.resolve("player_name", sender);
        assertTrue(result.isPresent());
        assertEquals("player_name", result.get());
    }

    @Test
    void resolve_tokenWithExtraSpaces_spacesCollapsed() throws Exception {
        Optional<String> result = resolver.resolve("hello   world", sender);
        assertTrue(result.isPresent());
        assertEquals("hello world", result.get());
    }

    @Test
    void resolve_numericToken_returnsPresent() throws Exception {
        Optional<String> result = resolver.resolve("12345", sender);
        assertTrue(result.isPresent());
        assertEquals("12345", result.get());
    }
}
