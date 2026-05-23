package io.paradaux.hibernia.framework.commander.resolvers;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IntegerResolverTest {

    private IntegerResolver resolver;
    private CommandSender sender;

    @BeforeEach
    void setUp() {
        resolver = new IntegerResolver();
        sender = Mockito.mock(CommandSender.class);
    }

    @Test
    void type_returnsIntegerClass() {
        assertEquals(Integer.class, resolver.type());
    }

    @Test
    void resolve_validPositiveInteger_returnsPresent() throws Exception {
        Optional<Integer> result = resolver.resolve("42", sender);
        assertTrue(result.isPresent());
        assertEquals(42, result.get());
    }

    @Test
    void resolve_negativeInteger_returnsPresent() throws Exception {
        Optional<Integer> result = resolver.resolve("-7", sender);
        assertTrue(result.isPresent());
        assertEquals(-7, result.get());
    }

    @Test
    void resolve_zero_returnsPresent() throws Exception {
        Optional<Integer> result = resolver.resolve("0", sender);
        assertTrue(result.isPresent());
        assertEquals(0, result.get());
    }

    @Test
    void resolve_maxInt_returnsPresent() throws Exception {
        Optional<Integer> result = resolver.resolve(String.valueOf(Integer.MAX_VALUE), sender);
        assertTrue(result.isPresent());
        assertEquals(Integer.MAX_VALUE, result.get());
    }

    @Test
    void resolve_minInt_returnsPresent() throws Exception {
        Optional<Integer> result = resolver.resolve(String.valueOf(Integer.MIN_VALUE), sender);
        assertTrue(result.isPresent());
        assertEquals(Integer.MIN_VALUE, result.get());
    }

    @Test
    void resolve_floatString_returnsEmpty() throws Exception {
        Optional<Integer> result = resolver.resolve("3.14", sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_alphabeticString_returnsEmpty() throws Exception {
        Optional<Integer> result = resolver.resolve("abc", sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_emptyString_returnsEmpty() throws Exception {
        Optional<Integer> result = resolver.resolve("", sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_overflow_returnsEmpty() throws Exception {
        Optional<Integer> result = resolver.resolve("99999999999999", sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_whitespaceString_returnsEmpty() throws Exception {
        Optional<Integer> result = resolver.resolve("  ", sender);
        assertTrue(result.isEmpty());
    }
}
