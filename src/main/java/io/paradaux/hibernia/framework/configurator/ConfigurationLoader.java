package io.paradaux.hibernia.framework.configurator;

import com.google.inject.Singleton;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
                plugin.getLogger().severe("Failed to instantiate component: " + componentClass.getName());
            }
        }
    }

    /**
     * Get a component by class
     */
    @SuppressWarnings("unchecked")
    public <T> T getComponent(Class<T> componentClass) {
        return (T) components.get(componentClass);
    }

    public Map<Class<?>, Object> getComponents() {
        return components;
    }
}
