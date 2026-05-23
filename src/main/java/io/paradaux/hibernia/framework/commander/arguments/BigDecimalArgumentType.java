package io.paradaux.hibernia.framework.commander.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * Paper-flavoured Brigadier argument type for {@link BigDecimal} inputs.
 *
 * <p>The default {@code StringArgumentType.word()} only accepts the unquoted
 * character set ({@code [0-9A-Za-z_\-.+]}), which silently truncates inputs
 * like {@code 1,000}: brigadier reads {@code "1"} as the arg and leaves
 * {@code ",000"} as trailing input, which then fails dispatch with no chat
 * feedback. Money inputs frequently arrive with thousands-separator commas
 * copied from chat output, so the BigDecimal arg path needs to be more
 * permissive. The downstream {@code BigDecimalResolver} strips the comma and
 * parses the result.
 *
 * <p>Parses any contiguous run of non-whitespace characters as the raw token;
 * the resolver layer turns it into a {@link BigDecimal} (and reports parse
 * failure via the framework's standard resolver-failure path, which surfaces
 * as the plugin's {@code invalid-amount} message instead of a Brigadier
 * syntax error).
 *
 * <p>Implements {@link CustomArgumentType} (not raw {@link ArgumentType})
 * because Paper rejects unrecognised raw argument types with
 * {@code "Custom unknown argument type was passed"} at lifecycle registration.
 * The native type is {@code StringArgumentType.word()} for client-side
 * prediction.
 */
public final class BigDecimalArgumentType implements CustomArgumentType<String, String> {

    private static final BigDecimalArgumentType INSTANCE = new BigDecimalArgumentType();
    private static final SimpleCommandExceptionType EXPECTED =
            new SimpleCommandExceptionType(() -> "Expected an amount");

    private BigDecimalArgumentType() {}

    public static BigDecimalArgumentType bigDecimal() {
        return INSTANCE;
    }

    @Override
    public @NotNull String parse(@NotNull StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
            reader.skip();
        }
        if (reader.getCursor() == start) {
            throw EXPECTED.createWithContext(reader);
        }
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public @NotNull ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }
}
