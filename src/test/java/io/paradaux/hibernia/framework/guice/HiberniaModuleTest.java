package io.paradaux.hibernia.framework.guice;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.paradaux.hibernia.framework.commander.CommandManager;
import io.paradaux.hibernia.framework.commander.annotations.Command;
import io.paradaux.hibernia.framework.commander.annotations.Route;
import io.paradaux.hibernia.framework.commander.spi.CommandHandler;
import io.paradaux.hibernia.framework.commander.spi.ParameterResolver;
import io.paradaux.hibernia.framework.configurator.ConfigurationLoader;
import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationComponent;
import io.paradaux.hibernia.framework.configurator.annotations.ConfigurationValue;
import io.paradaux.hibernia.framework.events.ListenerManager;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HiberniaModuleTest {

    @ConfigurationComponent
    public static class ModuleConfig {
        @ConfigurationValue(path = "module.value", defaultValue = "fallback")
        String value;
    }

    @Command("hib")
    public static class HibHandler implements CommandHandler {
        @Route("ping")
        public void ping() {
        }
    }

    public static class HibResolver implements ParameterResolver<HiberniaModuleTest> {
        @Override
        public Class<HiberniaModuleTest> type() {
            return HiberniaModuleTest.class;
        }

        @Override
        public Optional<HiberniaModuleTest> resolve(String token, CommandSender sender) {
            return Optional.empty();
        }
    }

    public static class HibListener implements Listener {
        @Inject
        public HibListener() {
        }
    }

    private JavaPlugin plugin;
    private Server server;
    private PluginManager pluginManager;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        FileConfiguration config = mock(FileConfiguration.class);

        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(plugin.getConfig()).thenReturn(config);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(config.contains("module.value")).thenReturn(true);
        when(config.getString("module.value", "fallback")).thenReturn("loaded");
    }

    private HiberniaModule module() {
        return HiberniaModule.forPlugin(plugin)
                .scanConfiguration("io.paradaux.hibernia.framework.guice")
                .handlers(HibHandler.class)
                .resolvers(HibResolver.class)
                .listeners(HibListener.class)
                .withoutMessages()
                .build();
    }

    @Test
    void configuration_isAvailableBeforeInjectorExists() {
        HiberniaModule module = module();

        assertEquals("loaded", module.configuration(ModuleConfig.class).value);
    }

    @Test
    void injector_bindsPluginConfigComponentsAndMultibinderSets() {
        Injector injector = Guice.createInjector(module());

        assertSame(plugin, injector.getInstance(JavaPlugin.class));
        assertSame(injector.getInstance(ConfigurationLoader.class).getComponent(ModuleConfig.class),
                injector.getInstance(ModuleConfig.class));

        Set<CommandHandler> handlers = injector.getInstance(Key.get(new TypeLiteral<Set<CommandHandler>>() {}));
        assertEquals(1, handlers.size());
        assertTrue(handlers.iterator().next() instanceof HibHandler);

        Set<ParameterResolver<?>> resolvers =
                injector.getInstance(Key.get(new TypeLiteral<Set<ParameterResolver<?>>>() {}));
        assertEquals(1, resolvers.size());

        // The whole entrypoint tier is constructible from the module alone.
        assertNotNull(injector.getInstance(CommandManager.class));
    }

    @Test
    void listenerManager_registersBoundListeners() {
        Injector injector = Guice.createInjector(module());

        injector.getInstance(ListenerManager.class).registerAll();

        verify(pluginManager).registerEvents(org.mockito.ArgumentMatchers.any(HibListener.class),
                org.mockito.ArgumentMatchers.eq(plugin));
    }
}
