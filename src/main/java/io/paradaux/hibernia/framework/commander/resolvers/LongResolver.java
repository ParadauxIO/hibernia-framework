package io.paradaux.hibernia.framework.commander.resolvers;

import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class LongResolver implements ParameterResolver<Long> {
    public Class<Long> type() {
        return Long.class;
    }

    public Optional<Long> resolve(String token, CommandSender sender) {
        try {
            return Optional.of(Long.parseLong(token));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
