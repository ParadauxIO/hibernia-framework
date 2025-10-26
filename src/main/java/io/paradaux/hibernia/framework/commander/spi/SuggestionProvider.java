package io.paradaux.hibernia.framework.commander.spi;

import org.bukkit.command.CommandSender;

import java.util.List;

@FunctionalInterface
public interface SuggestionProvider {
    List<String> suggest(String[] args, CommandSender sender);
}
