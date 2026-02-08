# HiberniaFramework — Plugin Integration Guide

This guide explains how to integrate `hibernia-framework` into a PaperMC plugin.
The framework provides Guice-based dependency injection, an annotation-driven command
system built on Brigadier, reflective configuration injection, and MiniMessage-powered i18n.

---

## Adding the Dependency

Add the Paradaux Maven repository and the framework artifact to your `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.paradaux.io/releases")   // or /snapshots for SNAPSHOT builds
}

dependencies {
    implementation("io.paradaux:hibernia-framework:0.1.0")
    // The framework pulls in Guice, Guava, Reflections transitively.
    // Paper API is compileOnly — you already have it.
}
```

Because the framework uses `implementation` scope for Guice, Guava, and Reflections, your
shadow/fat JAR must include them. Add relocations in your `shadowJar` task to avoid
classpath conflicts with other plugins:

```kotlin
tasks.shadowJar {
    relocate("com.google.inject", "your.plugin.libs.guice")
    relocate("com.google.common", "your.plugin.libs.guava")
    relocate("org.reflections", "your.plugin.libs.reflections")
    relocate("io.paradaux.hibernia.framework", "your.plugin.libs.hibernia")
}
```

---

## Bootstrapping with Guice

Create a Guice module to bind your plugin instance, command handlers, and parameter
resolvers. The framework expects these via constructor injection.

```java
public class MyPluginModule extends AbstractModule {

    private final JavaPlugin plugin;

    public MyPluginModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(JavaPlugin.class).toInstance(plugin);

        // Bind command handlers via Guice Multibinder
        Multibinder<CommandHandler> commands = Multibinder.newSetBinder(binder(), CommandHandler.class);
        commands.addBinding().to(EconomyCommands.class);
        commands.addBinding().to(AdminCommands.class);

        // Bind custom parameter resolvers (optional — built-ins cover String,
        // Integer, BigDecimal, OfflinePlayer)
        Multibinder<ParameterResolver<?>> resolvers =
                Multibinder.newSetBinder(binder(), new TypeLiteral<ParameterResolver<?>>() {});
        resolvers.addBinding().to(MyCustomResolver.class);
    }
}
```

In your plugin's `onEnable`:

```java
public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Injector injector = Guice.createInjector(new MyPluginModule(this));

        // Register commands
        injector.getInstance(CommandManager.class).registerAll();

        // Load configuration components
        ConfigurationLoader configLoader = new ConfigurationLoader(this);
        configLoader.scanPackage("com.example.myplugin.config");

        // i18n is available via injection wherever needed
        // Message message = injector.getInstance(Message.class);
    }
}
```

---

## Command Framework

### Defining a Command Handler

Annotate a class with `@Command` and implement `CommandHandler`. Each method annotated
with `@Route` becomes a subcommand. All parameters must be annotated with `@Sender`,
`@Arg`, or `@OptionalArg`.

```java
@Command({"eco", "economy"})         // root labels — /eco and /economy both work
@Permission("myplugin.economy")      // class-level permission (applies to all routes)
public class EconomyCommands implements CommandHandler {

    @Inject
    private Message message;          // i18n service, injected by Guice

    @Route("")                        // matches "/eco" with no subcommand
    @Description("Check your balance")
    public void balance(@Sender Player player) {
        // ...
    }

    @Route("give <player> <amount>")
    @Permission("myplugin.economy.give")   // overrides class-level permission
    @Description("Give money to a player")
    public void give(
            @Sender Player sender,
            @Arg("player") OfflinePlayer target,
            @Arg("amount") BigDecimal amount
    ) {
        // ...
    }

    @Route("top")
    @Async                            // runs off the main thread
    @Description("Show the richest players")
    public void top(@Sender CommandSender sender) {
        // Safe to do blocking I/O here (database queries, etc.)
    }

    @Route("pay <player> <amount>")
    public void pay(
            @Sender Player sender,
            @Arg("player") OfflinePlayer target,
            @OptionalArg(value = "amount", defaultValue = "0") BigDecimal amount
    ) {
        // amount defaults to BigDecimal(0) if omitted
    }
}
```

### Annotations Reference

| Annotation     | Target    | Purpose                                                    |
|----------------|-----------|------------------------------------------------------------|
| `@Command`     | Class     | Declares root command label(s). Accepts a `String[]`.      |
| `@Route`       | Method    | Subcommand path. Literals and `<placeholders>` in a string.|
| `@Arg`         | Parameter | Binds a required argument by placeholder name.             |
| `@OptionalArg` | Parameter | Binds an optional argument with a `defaultValue`.          |
| `@Sender`      | Parameter | Injects the `CommandSender` (or `Player`, `ConsoleCommandSender`).|
| `@Permission`  | Class/Method | Permission node. Method-level overrides class-level.    |
| `@Async`       | Method    | Dispatches execution off the main thread.                  |
| `@Description` | Method    | Human-readable description for help text.                  |

### Route Syntax

- Literals: plain tokens (`give`, `top`, `balance`)
- Arguments: wrapped in angle brackets (`<player>`, `<amount>`)
- Empty string `""`: matches the root command with no subcommand

Arguments in the route string must correspond to method parameters annotated with
`@Arg` or `@OptionalArg` whose `value()` matches the placeholder name.

### Built-in Parameter Resolvers

| Type            | Resolver                | Tab-complete Suggestions             |
|-----------------|-------------------------|--------------------------------------|
| `String`        | `StringResolver`        | None (shows `<name>` placeholder)    |
| `Integer`/`int` | `IntegerResolver`       | None (shows `<name>` placeholder)    |
| `BigDecimal`    | `BigDecimalResolver`    | None (shows `<name>` placeholder)    |
| `OfflinePlayer` | `OfflinePlayerResolver` | Online player names (prefix-filtered)|

String values are automatically sanitized: MiniMessage tags are stripped, only
alphanumeric characters, whitespace, and underscores are kept.

### Custom Parameter Resolvers

Implement `ParameterResolver<T>` and bind it via Guice multibinding:

```java
public class MaterialResolver implements ParameterResolver<Material> {

    @Override
    public Class<Material> type() {
        return Material.class;
    }

    @Override
    public Optional<Material> resolve(String token, CommandSender sender) {
        try {
            return Optional.of(Material.valueOf(token.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<String> suggestions(String prefix, CommandSender sender) {
        return Arrays.stream(Material.values())
                .map(m -> m.name().toLowerCase())
                .filter(n -> n.startsWith(prefix.toLowerCase()))
                .limit(20)
                .toList();
    }
}
```

If `resolve()` returns `Optional.empty()`, the framework throws an
`IllegalArgumentException` and sends an error message to the sender.

---

## Configuration Injection

The configurator loads values from your plugin's `config.yml` into annotated POJOs.

### 1. Define a Configuration Component

```java
@ConfigurationComponent
public class DatabaseConfig {

    @ConfigurationValue(path = "database.host", defaultValue = "localhost")
    private String host;

    @ConfigurationValue(path = "database.port", defaultValue = "3306")
    private int port;

    @ConfigurationValue(path = "database.ssl", defaultValue = "false")
    private boolean useSsl;

    @ConfigurationValue(path = "database.pool-size", defaultValue = "10")
    private int poolSize;

    // Getters (no setters needed — fields are set reflectively)
    public String getHost() { return host; }
    public int getPort() { return port; }
    public boolean isUseSsl() { return useSsl; }
    public int getPoolSize() { return poolSize; }
}
```

### 2. Load and Access

```java
ConfigurationLoader loader = new ConfigurationLoader(plugin);
loader.scanPackage("com.example.myplugin.config");

DatabaseConfig dbConfig = loader.getComponent(DatabaseConfig.class);
String host = dbConfig.getHost(); // "localhost" or value from config.yml
```

### Supported Field Types

`String`, `int`/`Integer`, `boolean`/`Boolean`, `double`/`Double`, `long`/`Long`,
`List<String>` (via `getStringList`), any `Enum` type (resolved by name), and complex
objects returned by Bukkit's `FileConfiguration.get()`.

### Requirements

- The component class **must** have a no-arg constructor (it is instantiated reflectively).
- Fields **must not** be `final` (they are set via reflection).
- `plugin.saveDefaultConfig()` is called automatically so your `config.yml` resource
  is copied on first run.

---

## Internationalization (i18n)

The `Message` class loads a `messages.properties` file from your plugin's data folder
and renders strings through Adventure's MiniMessage.

### messages.properties

```properties
# Global placeholders — available in every message
placeholder.prefix=<gold>[MyPlugin]</gold>
placeholder.error_prefix=<red>[Error]</red>

# Namespaced placeholders (economy.placeholder.* available in economy.* keys)
economy.placeholder.currency=<yellow>coins</yellow>

# Messages
economy.balance={prefix} You have <green>{amount}</green> {currency}.
economy.give.success={prefix} Gave <green>{amount}</green> {currency} to {player}.
economy.give.error={error_prefix} Could not complete the transfer.
general.no_permission={error_prefix} You don't have permission.
```

Placeholders use `{name}` syntax and resolve recursively (up to 8 levels deep).
Use `{{` and `}}` for literal braces.

### Sending Messages

```java
@Inject
private Message message;

// Send to a player
message.send(player, "economy.balance", "amount", balance);

// Get a Component for manual use
Component comp = message.component("economy.give.success",
        "amount", amount, "player", target.getName());

// Broadcast to all online players + console
message.broadcast("economy.give.success", "amount", amount, "player", name);

// Format to a raw MiniMessage string
String raw = message.format("economy.balance", Map.of("amount", "100"));
```

Key-value pairs are passed as varargs: `key1, value1, key2, value2, ...`
Alternatively, pass a `Map<String, ?>` directly.

### Reloading

Call `message.reload()` to re-read `messages.properties` from disk at runtime.
This is thread-safe (synchronized).

---

## Domain Exceptions

The framework provides six unchecked exception types for use in your plugin's
domain logic. All extend `RuntimeException`.

| Exception              | Semantic Analog | Use Case                                |
|------------------------|-----------------|-----------------------------------------|
| `BadCommandException`  | 400 Bad Request | Malformed input or invalid command usage |
| `NotFoundException`    | 404 Not Found   | Entity or resource not found             |
| `NoPermissionException`| 403 Forbidden   | Insufficient permissions                 |
| `ConflictException`    | 409 Conflict    | Duplicate or conflicting state           |
| `ExceedsLimitException`| 413/429         | Quota or rate limit exceeded             |
| `InternalException`    | 500 Internal    | Unexpected server-side error             |

`ConflictException` additionally accepts a `Throwable cause` in its constructor.

---

## HiberniaPlayer

A minimal player-model interface for cross-module player references without
depending on Bukkit's `Player` directly.

```java
public interface HiberniaPlayer {
    UUID getUniqueId();
    String getCurrentName();
}
```

Implement this in your plugin's player model. The `Message` class can send messages
to a `HiberniaPlayer` by looking up the Bukkit `Player` via `getCurrentName()`.

---

## StringUtils

Static utility methods:

| Method                          | Description                                              |
|---------------------------------|----------------------------------------------------------|
| `sanitize(String input)`        | Strips MiniMessage tags, removes non-alphanumeric chars (except whitespace/underscore), collapses spaces. |
| `random32()`                    | Generates a cryptographically random 32-char alphanumeric string. |
| `startsWithNumber(String s)`    | Returns `true` if the string starts with a digit.        |

The `StringResolver` automatically runs `sanitize()` on all resolved string arguments,
so user input in commands is cleaned by default.

---

## Threading Notes

- Methods annotated with `@Async` run on the Bukkit async scheduler. Do not call
  main-thread-only Bukkit API from these methods without scheduling back.
- The `CommandManager` safely routes `sendMessage` calls back to the main thread
  when errors occur during async execution.
- `Message.reload()` is `synchronized` and safe to call from any thread.
- The resolver map in `CommandManager` uses `ConcurrentHashMap`.
