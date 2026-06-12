package io.paradaux.hibernia.framework.events;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * Registers DI-managed Bukkit event listeners, mirroring how
 * {@link io.paradaux.hibernia.framework.commander.CommandManager} registers
 * commands: listeners are bound into a Guice {@code Multibinder<Listener>}
 * (e.g. via {@code HiberniaModule.forPlugin(...).listeners(...)}) and
 * registered in one call from {@code onEnable}.
 *
 * <p>Listeners are constructed by Guice, so they take their services through
 * constructor injection like any other entrypoint — keeping the
 * listener → service → persistence layering intact.</p>
 */
@Singleton
public class ListenerManager {

    private final JavaPlugin plugin;
    private final Set<Listener> listeners;

    @Inject
    public ListenerManager(JavaPlugin plugin, Set<Listener> listeners) {
        this.plugin = plugin;
        this.listeners = listeners;
    }

    /** Register every bound listener with the Bukkit plugin manager. */
    public void registerAll() {
        for (Listener listener : listeners) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }
}
