package io.paradaux.hibernia.framework.commander.resolvers;

import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.util.Optional;

public class BigDecimalResolver implements ParameterResolver<BigDecimal> {
    public Class<BigDecimal> type() {
        return BigDecimal.class;
    }

    public Optional<BigDecimal> resolve(String token, CommandSender sender) {
        if (token == null) return Optional.empty();
        // Strip thousands-separator commas before parsing. Players commonly
        // type formatted amounts like "19,993" copied from chat output; the
        // bare BigDecimal(String) ctor rejects them. Comma is treated as a
        // thousands separator unconditionally — locale-mixed inputs that use
        // ',' as a decimal point are not supported.
        try {
            return Optional.of(new BigDecimal(token.replace(",", "")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}