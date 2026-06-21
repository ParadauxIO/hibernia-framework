package io.paradaux.hibernia.framework.configurator;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationValue;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public class ConfigurationProcessor {

    private final Plugin plugin;

    public ConfigurationProcessor(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Process all annotated fields in the target object
     *
     * @param target The object to inject configuration values into
     */
    public void process(Object target) {
        Class<?> clazz = target.getClass();
        FileConfiguration config = plugin.getConfig();

        // Get all declared fields (including private ones)
        Field[] fields = clazz.getDeclaredFields();

        Arrays.stream(fields)
                .filter(field -> field.isAnnotationPresent(ConfigurationValue.class))
                .forEach(field -> {
                    ConfigurationValue annotation = field.getAnnotation(ConfigurationValue.class);
                    String path = annotation.path();
                    String defaultValue = annotation.defaultValue();

                    try {
                        // Make field accessible - this works in Java 9+ including Java 17
                        boolean accessible = field.trySetAccessible();
                        if (!accessible) {
                            plugin.getLogger().warning("Cannot access field: " + field.getName() + " - skipping");
                            return;
                        }

                        // Skip final fields
                        if (Modifier.isFinal(field.getModifiers())) {
                            plugin.getLogger().warning("Cannot inject config into final field: " + field.getName());
                            return;
                        }

                        // Get value from config with appropriate type conversion
                        Object value = getConfigValue(config, path, defaultValue, field.getType());
                        if (value != null) {
                            field.set(target, value);
                        }
                    } catch (Exception e) {
                        // A bad value (unparseable number, unknown enum constant,
                        // type mismatch) must name the path and field, with the
                        // cause attached — not vanish into a generic message.
                        plugin.getLogger().log(Level.WARNING,
                                "Failed to inject config value '" + path + "' into "
                                        + clazz.getSimpleName() + "." + field.getName() + ": " + e.getMessage(), e);
                    }
                });
    }

    /**
     * Get value from config with type conversion
     */
    @SuppressWarnings("unchecked")
    private Object getConfigValue(FileConfiguration config, String path, String defaultValue, Class<?> type) {
        if (!config.contains(path) && defaultValue.isEmpty()) {
            plugin.getLogger().warning("Configuration path not found: " + path);
            return null;
        }

        // Handle different types
        if (type == String.class) {
            return config.getString(path, defaultValue);
        } else if (type == int.class || type == Integer.class) {
            return config.contains(path) ? config.getInt(path) : Integer.parseInt(defaultValue);
        } else if (type == boolean.class || type == Boolean.class) {
            return config.contains(path) ? config.getBoolean(path) : Boolean.parseBoolean(defaultValue);
        } else if (type == double.class || type == Double.class) {
            return config.contains(path) ? config.getDouble(path) : Double.parseDouble(defaultValue);
        } else if (type == float.class || type == Float.class) {
            return config.contains(path) ? (float) config.getDouble(path) : Float.parseFloat(defaultValue);
        } else if (type == long.class || type == Long.class) {
            return config.contains(path) ? config.getLong(path) : Long.parseLong(defaultValue);
        } else if (type == List.class) {
            return config.getStringList(path);
        } else if (type.isEnum()) {
            String value = config.getString(path, defaultValue);
            try {
                return Enum.valueOf((Class<Enum>) type, value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid value '" + value + "' for " + path
                        + "; expected one of " + Arrays.toString(type.getEnumConstants()));
            }
        }

        // For complex types, return the object directly
        return config.get(path);
    }
}
