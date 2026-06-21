# Hibernia Framework — Documentation

A Spring-like library for PaperMC plugins. It owns the **entrypoint tier** — commands, dialogs, event
listeners, configuration, messaging — and the dependency-injection glue that wires them to your
services, so your plugin keeps a clean *entrypoint → service → persistence* layering instead of
degrading into command classes that parse args, run SQL, and format chat in one method.

The framework does **not** own your business logic or your database. That separation is the point.

## Guides

| Guide | What it covers |
|-------|----------------|
| [Getting started](getting-started.md) | Install, the `onEnable` bootstrap, project layout and layering |
| [Dependency injection](dependency-injection.md) | `HiberniaModule`, what it binds, pre-injector config, manual wiring |
| [Commands](commands.md) | `commander`: `@Command`/`@Route`, arguments, resolvers, permissions, async, help |
| [Configuration](configuration.md) | `@ConfigurationComponent` POJOs, supported types, reload |
| [Messages & i18n](messages.md) | `messages.properties`, placeholders, MiniMessage, value escaping |
| [Exceptions](exceptions.md) | The HTTP-semantic exceptions and how they render to players |
| [Events](events.md) | DI-managed Bukkit listeners via `ListenerManager` |
| [Dialogs (Usher)](dialogs.md) | `usher`: `@Dialog`/`@Screen`/`@Action`, typed inputs, navigation, async |

## At a glance

```java
@Override
public void onEnable() {
    HiberniaModule hibernia = HiberniaModule.forPlugin(this)
            .scanConfiguration("net.example.myplugin.model.config")
            .handlers(EconomyCommands.class)
            .resolvers(FirmPlayerResolver.class)
            .listeners(JoinListener.class)
            .dialogs(FindDialog.class)
            .build();

    DatabaseConfiguration db = hibernia.configuration(DatabaseConfiguration.class);

    Injector injector = Guice.createInjector(hibernia, new DatabaseModule(db), new ServicesModule());

    injector.getInstance(CommandManager.class).registerAll();
    injector.getInstance(ListenerManager.class).registerAll();
}
```

## Modules

- **`commander`** — annotation-driven commands over Brigadier, with registration-time validation.
- **`usher`** — annotation-driven dialogs over Paper's Dialog API.
- **`configurator`** — reflective `config.yml` → POJO injection with in-place reload.
- **`i18n`** — templated, MiniMessage-formatted, injection-safe player messaging.
- **`events`** — DI-managed listener registration.
- **`exceptions`** — HTTP-semantic exceptions your services throw and the framework renders.
- **`guice`** — `HiberniaModule`, the framework-owned bootstrap module.

See the top-level [README](../README.md) for the project pitch and repository setup.
