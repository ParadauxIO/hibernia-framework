package io.paradaux.hibernia.framework.commander.resolvers;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BigDecimalResolverTest {

    private BigDecimalResolver resolver;
    private CommandSender sender;

    @BeforeEach
    void setUp() {
        resolver = new BigDecimalResolver();
        sender = Mockito.mock(CommandSender.class);
    }

    @Test
    void type_returnsBigDecimalClass() {
        assertEquals(BigDecimal.class, resolver.type());
    }

    @Test
    void resolve_integerString_returnsPresent() throws Exception {
        Optional<BigDecimal> result = resolver.resolve("100", sender);
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("100"), result.get());
    }

    @Test
    void resolve_decimalString_returnsPresent() throws Exception {
        Optional<BigDecimal> result = resolver.resolve("3.14159", sender);
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("3.14159"), result.get());
    }

    @Test
    void resolve_negativeDecimal_returnsPresent() throws Exception {
        Optional<BigDecimal> result = resolver.resolve("-99.99", sender);
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("-99.99"), result.get());
    }

    @Test
    void resolve_zero_returnsPresent() throws Exception {
        Optional<BigDecimal> result = resolver.resolve("0", sender);
        assertTrue(result.isPresent());
        assertEquals(BigDecimal.ZERO, result.get().stripTrailingZeros());
    }

    @Test
    void resolve_scientificNotation_returnsPresent() throws Exception {
        Optional<BigDecimal> result = resolver.resolve("1E+10", sender);
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("1E+10"), result.get());
    }

    @Test
    void resolve_largeNumber_returnsPresent() throws Exception {
        String large = "99999999999999999999999999.99";
        Optional<BigDecimal> result = resolver.resolve(large, sender);
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal(large), result.get());
    }

    @Test
    void resolve_alphabeticString_returnsEmpty() throws Exception {
        Optional<BigDecimal> result = resolver.resolve("abc", sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_emptyString_returnsEmpty() throws Exception {
        Optional<BigDecimal> result = resolver.resolve("", sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_nullToken_returnsEmpty() throws Exception {
        Optional<BigDecimal> result = resolver.resolve(null, sender);
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_mixedAlphanumeric_returnsEmpty() throws Exception {
        Optional<BigDecimal> result = resolver.resolve("12abc", sender);
        assertTrue(result.isEmpty());
    }

    // Live-server regression 2026-05-16: an admin typed `/fine issue p 19,993 …`
    // (formatted-style amount with a thousands-separator) and got an unrelated
    // "unknown player" error because BigDecimal(String) rejects commas. The
    // resolver now strips commas before parsing — treating them unconditionally
    // as thousands separators.
    @Test
    void resolve_thousandsSeparatorComma_isStripped() {
        Optional<BigDecimal> result = resolver.resolve("19,993", sender);
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("19993"), result.get());
    }

    @Test
    void resolve_multipleThousandsSeparators_areStripped() {
        Optional<BigDecimal> result = resolver.resolve("1,234,567.89", sender);
        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("1234567.89"), result.get());
    }
}
