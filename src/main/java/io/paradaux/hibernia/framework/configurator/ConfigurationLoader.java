package io.paradaux.hibernia.framework.configurator;

import com.google.inject.Singleton;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Discovers {@code @ConfigurationComponent} classes on the classpath,
 * instantiates them and injects their {@code @ConfigurationValue} fields from
 * the plugin's {@code config.yml}. Instances are singletons, intended to be
 * bound into Guice (which {@code HiberniaModule} does automatically).
 */
@Singleton
public class ConfigurationLoader {

    private final JavaPlugin plugin;
    private final ConfigurationProcessor processor;
    private final Map<Class<?>, Object> components = new HashMap<>();

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

        for (Class<?> componentClass : componentClasses) {
            try {
                // Create instance using default constructor
                Constructor<?> constructor = componentClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object instance = constructor.newInstance();

                // Process config annotations
                processor.process(instance);

                // Store component
                components.put(componentClass, instance);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to instantiate component: " + componentClass.getName(), e);
            }
        }
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

    public Map<Class<?>, Object> getComponents() {
        return components;
    }

    /**
     * Re-read {@code config.yml} from disk and re-inject every loaded component
     * in place. Component instances keep their identity (so existing Guice
     * bindings and injected references stay valid); only their field values
     * change.
     */
    public void reload() {
        plugin.reloadConfig();
        components.values().forEach(processor::process);
    }
}
