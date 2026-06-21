# Configuration

[← Docs index](README.md)

The `configurator` module maps `config.yml` onto annotated POJOs — analogous to Spring's `@Value`. You
declare a component, the framework instantiates it, injects its fields from config, and binds it as a
singleton for the rest of your plugin to inject.

## A configuration component

```java
@ConfigurationComponent
@Getter
public final class DatabaseConfiguration {

    @ConfigurationValue(path = "database.host", defaultValue = "localhost")
    private String host;

    @ConfigurationValue(path = "database.port", defaultValue = "3306")
    private int port;

    @ConfigurationValue(path = "database.username", defaultValue = "root")
    private String username;

    @ConfigurationValue(path = "database.table-prefix", defaultValue = "treasury_")
    private String tablePrefix;
}
```

- `path` is the dotted path within `config.yml`.
- `defaultValue` is used when the path is absent. An **empty string is a valid configured value** and is
  not replaced by the default.
- Components need a no-arg constructor (it may be private). Place them in one package (convention:
  `model/config/`) and point `scanConfiguration(...)` at it.

## Supported field types

`String`, `int`/`Integer`, `long`/`Long`, `double`/`Double`, `float`/`Float`, `boolean`/`Boolean`,
`List<String>`, and **enums**. An invalid enum value is logged with the allowed constants and the field
is left at its default. Final fields are skipped (with a warning) — config fields must be mutable.

## Loading

`HiberniaModule` does this for you:

```java
HiberniaModule.forPlugin(this)
        .scanConfiguration("net.example.myplugin.model.config")   // repeatable for multiple packages
        /* … */
        .build();
```

Under the hood it constructs a `ConfigurationLoader`, calls `saveDefaultConfig()` (so `config.yml`
exists), scans the package for `@ConfigurationComponent` classes, injects each one, and binds the
instances as singletons. Inject them anywhere:

```java
@Inject
public DatabaseModule(DatabaseConfiguration db) { ... }
```

You can also read a component before the injector exists, via the module:

```java
DatabaseConfiguration db = hibernia.configuration(DatabaseConfiguration.class);
```

`configuration(...)` / `ConfigurationLoader.getComponent(...)` **throws** `IllegalStateException` if the
component wasn't loaded (a typo'd package, or a component that failed to instantiate — check the startup
log), rather than returning `null`.

## Reload

`ConfigurationLoader.reload()` re-reads `config.yml` from disk and re-injects every loaded component
**in place** — the instances keep their identity, so existing Guice bindings and any references you've
already injected stay valid and simply see the new values:

```java
public final class ReloadCommand implements CommandHandler {
    @Inject private ConfigurationLoader config;

    @Route("reload")
    @Permission("myplugin.reload")
    public void reload(@Sender CommandSender sender) {
        config.reload();
        // also reload messages if you use them:
        // message.reload();
    }
}
```

> Two-way sync (writing values back to the file) is not supported yet.

---

Related: [Dependency injection](dependency-injection.md) · [Messages & i18n](messages.md)
