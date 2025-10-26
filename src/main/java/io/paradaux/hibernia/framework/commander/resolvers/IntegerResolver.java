package io.paradaux.hibernia.framework.commander.resolvers;

import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class IntegerResolver implements ParameterResolver<Integer> {
    public Class<Integer> type() {
        return Integer.class;
    }

    public Optional<Integer> resolve(String token, CommandSender sender) {
        try {
            return Optional.of(Integer.parseInt(token));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
