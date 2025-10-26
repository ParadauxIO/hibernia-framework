package io.paradaux.hibernia.framework.commander.resolvers;

import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.hibernia.framework.utils.StringUtils;
import org.bukkit.command.CommandSender;

import java.util.Optional;

public class StringResolver implements ParameterResolver<String> {
    @Override
    public Class<String> type() {
        return String.class;
    }

    @Override
    public Optional<String> resolve(String token, CommandSender sender) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(StringUtils.sanitize(token));
    }
}
