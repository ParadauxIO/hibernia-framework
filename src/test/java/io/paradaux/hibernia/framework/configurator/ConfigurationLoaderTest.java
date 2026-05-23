package io.paradaux.hibernia.framework.configurator;

import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationValue;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigurationLoaderTest {

    private JavaPlugin plugin;
    private FileConfiguration config;
    private Logger logger;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        config = mock(FileConfiguration.class);
        logger = mock(Logger.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(logger);
    }

    @ConfigurationComponent
    public static class GoodComponent {
        @ConfigurationValue(path = "loader.value", defaultValue = "fallback")
        String value;
    }

    @ConfigurationComponent
    public static class FailingComponent {
        private FailingComponent(String ignored) {
        }
    }

    @Test
    void constructor_savesDefaultConfig() {
        new ConfigurationLoader(plugin);

        verify(plugin).saveDefaultConfig();
    }

    @Test
    void scanPackage_registersAndProcessesAnnotatedComponents() {
        when(config.contains("loader.value")).thenReturn(true);
        when(config.getString("loader.value", "fallback")).thenReturn("loaded");

        ConfigurationLoader loader = new ConfigurationLoader(plugin);
        loader.scanPackage("io.paradaux.hibernia.framework.configurator");

        GoodComponent component = loader.getComponent(GoodComponent.class);
        assertNotNull(component);
        assertEquals("loaded", component.value);
        assertTrue(loader.getComponents().containsKey(GoodComponent.class));
    }

    @Test
    void scanPackage_logsFailureWhenInstantiationFails() {
        ConfigurationLoader loader = new ConfigurationLoader(plugin);

        loader.scanPackage("io.paradaux.hibernia.framework.configurator");

        verify(logger).severe(org.mockito.ArgumentMatchers.contains(FailingComponent.class.getName()));
    }
}
