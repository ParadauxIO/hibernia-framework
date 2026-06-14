# Getting started

[← Docs index](README.md)

## Requirements

- **Java 21**
- **PaperMC 1.21.6+** (the dialog module uses the Paper Dialog API; commands use Paper's Brigadier
  lifecycle). The framework is built against `paper-api:1.21.8`.

## Add the dependency

The repository:

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")

    maven("https://repo.paradaux.io/releases")   // stable
    maven("https://repo.paradaux.io/snapshots")  // snapshots (1.1.0-SNAPSHOT)
}
```

The artifact:

```kotlin
dependencies {
    implementation("io.paradaux:hibernia-framework:1.1.0-SNAPSHOT")
}
```

> The `HiberniaModule` bootstrap, the event/listener tier, and the `usher` dialog framework described in
> these docs are on **`1.1.0-SNAPSHOT`** until 1.1.0 is released. The command, configuration and message
> modules are also present in the `1.0.x` stable line.

Guice 7 and Reflections come transitively (Guice is an `api` dependency — it's part of the framework's
public surface). You **shade** the framework into your plugin jar like any library.

## The bootstrap

Everything is wired in `onEnable` in three steps: build the framework module, create the injector,
register entrypoints.

```java
public final class MyPlugin extends JavaPlugin {

    private Injector injector;

    @Override
    public void onEnable() {
        // 1) Build the framework module. Configuration packages are scanned eagerly here, so typed
        //    config is available before the injector exists.
        HiberniaModule hibernia = HiberniaModule.forPlugin(this)
                .scanConfiguration("net.example.myplugin.model.config")
                .handlers(EconomyCommands.class, AdminCommands.class)
                .resolvers(FirmPlayerResolver.class)
                .listeners(JoinListener.class)
                .dialogs(FindDialog.class)
                .build();

        // 2) Create the injector. Your own modules bind only your services and persistence layer.
        DatabaseConfiguration db = hibernia.configuration(DatabaseConfiguration.class);
        this.injector = Guice.createInjector(
                hibernia,
                new DatabaseModule(db),
                new ServicesModule());

        // 3) Register the entrypoints.
        injector.getInstance(CommandManager.class).registerAll();
        injector.getInstance(ListenerManager.class).registerAll();
        // Dialogs are shown on demand via an injected DialogManager — nothing to register up front.
    }
}
```

See [Dependency injection](dependency-injection.md) for exactly what `HiberniaModule` binds and how to
wire things by hand if you need more control.

## Project layout & layering

The framework exists to make one architecture natural: a strict, one-directional
**entrypoint → service → persistence** layering.

```
src/main/java/net/example/myplugin/
├── MyPlugin.java                 # onEnable bootstrap (above)
├── commands/                     # @Command handlers — thin: parse, resolve, call a service
├── events/  (or listeners/)      # Bukkit @EventHandler listeners — thin, like commands
├── dialogs/                      # @Dialog handlers — thin, like commands
├── guice/                        # your ServicesModule, DatabaseModule, …
├── model/
│   └── config/                   # @ConfigurationComponent POJOs
├── services/                     # interfaces …
│   └── impl/                     # … and implementations: all business logic + transactions
└── mappers/  (or dao/)           # persistence only — SQL, no logic
```

Rules of thumb:

- **Entrypoints are thin.** A command/listener/dialog handler parses input, resolves the actor, and
  calls a service. It never touches the persistence layer directly.
- **Services own business logic and transactions.** Cross-domain work (e.g. moving money) goes through
  the owning system's API, never another plugin's mapper.
- **Persistence owns DB access only.** No business logic in a mapper/DAO.
- **Constructor injection everywhere** — except inside `@Command` handler classes, where the command
  framework permits `@Inject` field injection (it constructs handlers reflectively). Services and
  listeners use constructor injection.

## Where to go next

- Building commands → [Commands](commands.md)
- Building dialogs → [Dialogs (Usher)](dialogs.md)
- Reading config → [Configuration](configuration.md)
- Talking to players → [Messages & i18n](messages.md)
- Reporting failures → [Exceptions](exceptions.md)
