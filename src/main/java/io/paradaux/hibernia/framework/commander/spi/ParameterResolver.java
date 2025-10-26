package io.paradaux.hibernia.framework.commander.spi;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

public interface ParameterResolver<T> {
    Class<T> type();
    Optional<T> resolve(String token, CommandSender sender) throws Exception;
    default List<String> suggestions(String prefix, CommandSender sender) { return List.of(); }
}

