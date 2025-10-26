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
        try {
            return Optional.of(new BigDecimal(token));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}