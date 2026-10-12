package io.paradaux.hibernia.framework.configurator;

import com.google.inject.Singleton;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Discovers {@code @ConfigurationComponent} classes on the classpath,
 * instantiates them and injects their {@code @ConfigurationValue} fields from
 * the plugin's {@code config.yml}. Instances are singletons, intended to be
 * bound into Guice (which {@code HiberniaModule} does automatically).
 *
 * <h2>Atomic reload</h2>
 * <p>{@link #reload()} does <strong>not</strong> mutate live component instances in
 * place. It builds a fresh, fully-populated instance of every component, then
 * publishes the whole set with a single volatile reference swap. A concurrent reader
 * therefore always sees a consistent, all-or-nothing snapshot — never a half-updated
 * POJO — which removes the config-reload visibility race without each consumer having
 * to add its own {@code volatile}/atomic guard.</p>
 *
 * <p>Read the current values through {@link #getComponent(Class)} (the snapshot
 * accessor). A component reference captured earlier — e.g. one Guice injected at
 * startup — keeps showing the values it was loaded with: a consistent <em>stale</em>
 * snapshot, not a torn read. Re-fetch via {@link #getComponent(Class)} to observe a
 * reload.</p>
 */
@Singleton
public class ConfigurationLoader {

    private final JavaPlugin plugin;
    private final ConfigurationProcessor processor;
    /** Component classes that have loaded at least once, in discovery order; rebuilt on reload. */
    private final List<Class<?>> componentClasses = new ArrayList<>();
    /** The current immutable snapshot of loaded components; swapped atomically on reload. */
    private volatile Map<Class<?>, Object> components = Map.of();

    public ConfigurationLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.processor = new ConfigurationProcessor(plugin);

        // Ensure config.yml exists
        plugin.saveDefaultConfig();
    }

    /**
     * Scan package for components and load their configurations
     */
    public void scanPackage(String packageName) {
        Reflections reflections = new Reflections(packageName);
        Set<Class<?>> componentClasses = reflections.getTypesAnnotatedWith(ConfigurationComponent.class);

        Map<Class<?>, Object> updated = new LinkedHashMap<>(components);
        for (Class<?> componentClass : componentClasses) {
            try {
                Object instance = instantiate(componentClass);
                processor.process(instance);
                updated.put(componentClass, instance);
                if (!this.componentClasses.contains(componentClass)) {
                    this.componentClasses.add(componentClass);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to instantiate component: " + componentClass.getName(), e);
            }
        }
        this.components = Map.copyOf(updated);
    }

    /**
     * Get a component by class.
     *
     * @throws IllegalStateException when no component of that class was loaded —
     *         either {@code scanPackage(...)} never covered its package, or the
     *         component failed to load (see the startup log)
     */
    public <T> T getComponent(Class<T> componentClass) {
        Object component = components.get(componentClass);
        if (component == null) {
            throw new IllegalStateException("No @ConfigurationComponent loaded for "
                    + componentClass.getName() + " — check that scanPackage(...) covers its package"
                    + " and that it instantiated without errors (see startup log).");
        }
        return componentClass.cast(component);
    }

    /** The current component snapshot (immutable). */
    public Map<Class<?>, Object> getComponents() {
        return components;
    }

    /**
     * Re-read {@code config.yml} from disk and rebuild every loaded component into a
     * fresh instance, then publish the whole set atomically. Readers going through
     * {@link #getComponent(Class)} switch from the old snapshot to the new one in a
     * single step — they never observe a partially-updated component. A component that
     * fails to rebuild keeps its last good values rather than dropping out.
     */
    public void reload() {
        plugin.reloadConfig();

        Map<Class<?>, Object> previous = components;
        Map<Class<?>, Object> rebuilt = new LinkedHashMap<>();
        for (Class<?> componentClass : componentClasses) {
            try {
                Object instance = instantiate(componentClass);
                processor.process(instance);
                rebuilt.put(componentClass, instance);
            } catch (Exception e) {
                Object prev = previous.get(componentClass);
                if (prev != null) {
                    rebuilt.put(componentClass, prev);
                }
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to reload component: " + componentClass.getName(), e);
            }
        }
        this.components = Map.copyOf(rebuilt);
    }

    private Object instantiate(Class<?> componentClass) throws Exception {
        Constructor<?> constructor = componentClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
