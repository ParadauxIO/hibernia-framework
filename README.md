# Hibernia Framework

This project is a framework for building Minecraft plugins with an emphasis on an Event-Service-Dao architecture. An event in this context is anything which causes an action. In a REST API this would usually be a controller, here it would be a command and or game event.

The framework deliberately covers the **entrypoint tier** (commands, event listeners, configuration, messaging) plus the DI glue; the service and persistence tiers belong to your plugin. The framework's job is to make the separation of concerns natural — thin annotated entrypoints, constructor-injected services — not to own your business logic or database.

Check out the AI-Generated documentation which is more-or-less accurate at http://deepwiki.com/paradauxio/hibernia-framework

## Feature List
- Command registration, handling and routing with Paper and Brigadier support, validated at registration time (a typo'd route fails loudly at startup, never silently at runtime).
- Event listener registration through DI (`ListenerManager`).
- Semantic exceptions (`NotFoundException`, `ConflictException`, …) thrown from your service layer and rendered to the player automatically, with operator-overridable `hibernia.error.*` message keys.
- Localisation via a templated `properties` file. This uses MiniMessage/Adventure for formatting. Player-controlled placeholder values are escaped by default.
- Configuration deserialisation and mapping, including in-place `reload()`.
- A framework-owned Guice module (`HiberniaModule`) so a plugin's bootstrap is a few lines.

Coming soon:
- Bi-directional configuration, have set values be reflected in the configuration file.
- Regularly scheduled task creation (Akin to @Scheduled in Spring)
- PlaceholderAPI support within the localisation module.
- Per-player locale support in the localisation module (it is currently a single-file template system, not full i18n).

## Using the framework 

Configure the maven repository:
```gradle
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    
    // For stable releases
    maven("https://repo.paradaux.io/releases")
    
    // For snapshot builds (optional)
    maven("https://repo.paradaux.io/snapshots")
}
```

```xml
<repositories>
    <repository>
        <id>paradaux-releases</id>
        <url>https://repo.paradaux.io/releases</url>
    </repository>
    
    <!-- Optional: for snapshot builds -->
    <repository>
        <id>paradaux-snapshots</id>
        <url>https://repo.paradaux.io/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

Configure the dependency:
```gradle
dependencies {
    implementation("io.paradaux:hibernia-framework:1.0.2")
}
```

```xml
<dependencies>
    <dependency>
        <groupId>io.paradaux</groupId>
        <artifactId>hibernia-framework</artifactId>
        <version>1.0.2</version>
    </dependency>
</dependencies>
```

This framework includes some useful additional libraries which you can use in your consuming plugins, the former of which is encouraged.
- Guice 7.0.0 (exposed as an `api` dependency — it is part of the framework's public surface)
- Reflections

## Bootstrap

`HiberniaModule` wires the whole entrypoint tier in one place. A minimal `onEnable`:

```java
@Override
public void onEnable() {
    HiberniaModule hibernia = HiberniaModule.forPlugin(this)
            .scanConfiguration("net.example.myplugin.model.config") // @ConfigurationComponent package
            .handlers(EconomyCommands.class, AdminCommands.class)   // CommandHandler classes
            .resolvers(FirmPlayerResolver.class)                    // custom ParameterResolvers
            .listeners(JoinListener.class)                          // Bukkit Listeners
            .build();

    // Typed config is available before the injector exists, e.g. for a
    // module that needs database settings:
    DatabaseConfiguration db = hibernia.configuration(DatabaseConfiguration.class);

    this.injector = Guice.createInjector(hibernia, new DatabaseModule(db), new ServicesModule());

    injector.getInstance(CommandManager.class).registerAll();
    injector.getInstance(ListenerManager.class).registerAll();
}
```

`HiberniaModule` binds: the `JavaPlugin`/`Plugin` instance, the `ConfigurationLoader` and every discovered `@ConfigurationComponent` (as singletons), `Message` (eager singleton — call `.withoutMessages()` if your plugin doesn't bundle a `messages.properties`), and the multibinder sets for handlers, resolvers and listeners. Your own modules only bind your services and persistence layer.

## Features

### 1. Annotation-based declarative Command handling:
```java
@Command(value = "eco", description = "Economy commands")
public class EconomyCommands implements CommandHandler {
    
    @Route("")
    public void root(@Sender Player sender) {
        // Handles: /eco
    }
    
    @Route("balance [player]")
    public void balance(@Sender Player sender,
                        @OptionalArg(value = "player", defaultValue = OptionalArg.SENDER) OfflinePlayer target) {
        // Handles: /eco balance         (target = sender)
        //          /eco balance <name>  (target = resolved player)
    }
    
    @Route("give <player> <amount>")
    public void give(@Sender Player sender, @Arg("player") OfflinePlayer target, @Arg("amount") int amount) {
        // Handles: /eco give <player> <amount>
    }
}
```
See https://deepwiki.com/ParadauxIO/hibernia-framework/4.1-defining-commands for more information. 

Route syntax: literals are plain tokens, required arguments are `<name>`, optional arguments are `[name]`. Optional arguments must form the tail of the route; the command is executable with or without them, and an omitted optional takes its `defaultValue` (resolved to the parameter type — `OptionalArg.SENDER` defaults to the command sender; an empty default yields `null` for reference types).

Routes are **validated when commands register**: a placeholder with no matching `@Arg`, a required `@Arg` missing from the route, two routes binding the same path (even across handler classes sharing a root), or conflicting argument types at the same position all fail with a descriptive error. A failing handler class is skipped and logged; the plugin's other commands still register. `CommandManager#routeIndex()` exposes the registered routes (pattern, `@Description`, permission, async flag) so help commands can be generated from reality instead of hand-maintained.

It supports Brigadier, and automatically registers the commands with Paper. This means tab-support is provided via the use of "resolvers." Several resolvers are baked-in:
- String
- Integer / Long
- BigDecimal (accepts thousands-separator commas, e.g. `1,000`)
- Boolean (`true|false|yes|no|y|n|1|0|on|off`)
- OfflinePlayer (cache-only resolution — safe for Bedrock/Floodgate names)

It is possible to implement your own by implementing the ParameterResolver interface. **Threading note:** `suggestions(...)` is always called off the main thread, and `resolve(...)` runs on an async worker for `@Async` routes — back resolvers with service-managed caches, not live Bukkit state. The below example auto-completes firms which the player has access to via service method calls.

```java
@Singleton
public final class FirmPlayerResolver implements ParameterResolver<FirmPlayer> {

    private final FirmPlayerService players;

    @Inject
    public FirmPlayerResolver(FirmPlayerService players) {
        this.players = players;
    }

    @Override
    public Class<FirmPlayer> type() {
        return FirmPlayer.class;
    }

    @Override
    public Optional<FirmPlayer> resolve(String token, CommandSender sender) {
        if (token == null || token.isBlank()) return Optional.empty();

        // 1) UUID exact
        UUID asUuid = tryParseUuid(token);
        if (asUuid != null) {
            return players.findByUuid(asUuid);
        }

        // 2) Exact (case-insensitive) name match if present in cache
        Optional<FirmPlayer> byExactName = players.findByName(token);
        if (byExactName.isPresent()) return byExactName;

        // 3) Prefix search – only accept if it’s unambiguous
        List<FirmPlayer> matches = players.searchByPrefix(token, 5);
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }

        // ambiguous or no match
        return Optional.empty();
    }

    @Override
    public List<String> suggestions(String prefix, CommandSender sender) {
        String p = (prefix == null) ? "" : prefix.trim();
        // Return last-known names for tab completion
        return players.searchByPrefix(p, 20).stream()
                .map(FirmPlayer::getCurrentName)
                .distinct()
                .toList();
    }

    private static UUID tryParseUuid(String s) {
        try {
            String norm = s.trim();
            // Support compact 32-char UUID too, because players paste weird stuff
            if (norm.length() == 32) {
                norm = norm.substring(0, 8) + "-" + norm.substring(8, 12) + "-" +
                        norm.substring(12, 16) + "-" + norm.substring(16, 20) + "-" +
                        norm.substring(20);
            }
            return UUID.fromString(norm.toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }
}
``` 

### 2. Exceptions

Within the framework there are semantic exceptions you can throw from within service methods, with an eye to building a rich-error experience:
- BadCommandException: Used when commands are malformatted semantically, but were executed syntactically correct. Analogous to a HTTP 400
- ConflictException: When a command tries to do something which causes a conflict (e.g., for use with an RDMS with a primary key / unique constraint violation.) Analogous to a HTTP 409
- ExceedsLimitException: When a command sender attempts to exceed a limit imposed by a command (cooldowns, value constraints etc) 
- InternalException: When an unexpected error occurs within the framework, or within a consuming plugin.
- NoPermissionException: When a permission check fails inside your service layer (the framework also uses the same message key for its own `@Permission` denials).
- NotFoundException: When a resource is not found. Analogous to a HTTP 404

These extend RuntimeException. **Throw them from your service layer and let them propagate out of the command handler** — the framework catches them and sends the player a formatted message. Each maps to a key in `messages.properties` (when a `Message` bean is bound), falling back to a built-in default otherwise:

| Exception | Message key | Default behaviour |
|---|---|---|
| `BadCommandException` | `hibernia.error.bad-command` | exception message, red |
| `NotFoundException` | `hibernia.error.not-found` | exception message, red |
| `ConflictException` | `hibernia.error.conflict` | exception message, red |
| `ExceedsLimitException` | `hibernia.error.exceeds-limit` | exception message, red |
| `NoPermissionException` | `hibernia.error.no-permission` | generic denial message |
| invalid/unresolvable argument | `hibernia.error.invalid-argument` | explanation, red |
| anything else / `InternalException` | `hibernia.error.internal` | generic message; **stack trace logged server-side, never shown to the player** |

Override any of these keys in your plugin's `messages.properties` (the `{message}` placeholder carries the exception text) — e.g.:

```properties
hibernia.error.not-found={prefix} <red>{message}</red>
hibernia.error.no-permission={prefix} <red>You can't do that.</red>
```

### 3. Configuration

This framework provides yaml deserialisation to objects based on annotations, analogous to @Value in Spring Framework. 

```java
@ConfigurationComponent
@Getter
public class DatabaseConfiguration {

    @ConfigurationValue(path = "database.host", defaultValue = "localhost")
    private String host;

    @ConfigurationValue(path = "database.port", defaultValue = "3306")
    private String port;

    @ConfigurationValue(path = "database.database", defaultValue = "treasury")
    private String database;

    @ConfigurationValue(path = "database.username", defaultValue = "root")
    private String username;

    @ConfigurationValue(path = "database.password", defaultValue = "password")
    private String password;

    @ConfigurationValue(path = "database.table-prefix", defaultValue = "treasury_")
    private String tablePrefix;
}
```

The path is used to represent the path within config.yml where the value is found. If the value is undefined it takes the default value. Empty string is a valid value which won't be replaced with the default value. Supported field types: `String`, `int`, `long`, `double`, `float`, `boolean` (and boxed forms), `List<String>`, and enums (an invalid enum value is logged with the allowed constants).

The framework contains the logic to scan a package for ConfigurationComponent-annotated classes and create a Singleton instance for dependency injection purposes (`HiberniaModule` binds them automatically). `ConfigurationLoader#reload()` re-reads `config.yml` and re-injects every component **in place** — bound singletons keep their identity, so a `/myplugin reload` command is one service call. Two-way synchronisation (writing values back to the file) is not yet supported.

### Localisation

The framework includes a simple templated message system, which allows for the use of a `messages.properties` and a `Message` class which has helper methods for sending messages using keys from this file, along with key-value placeholders and their replacements. (Note: this is one file per plugin — per-player locale selection is on the roadmap.)

Example:
```properties
# Static Placeholders
# Anything after placeholder. is treated as a placeholder, which can be used in any of the below
# localisation configurations, you can define as many as you want here, and each are supported automatically.
placeholder.prefix=<bold><blue>BUSINESS</blue></bold> <gray>»</gray>

# Below are placeholders to use to keep a consistent colour palette. sec for secondary, then the beginning and ending tags.
placeholder.secbegin=<#6f6fff>
placeholder.secend=</#6f6fff>
placeholder.secgt=<#6f6fff><</#6f6fff>
placeholder.seclt=<#6f6fff>></#6f6fff>

business.general.no-permission={prefix} You do not have permission to run this command.
business.firm.disband.success={prefix} {firm} has been disbanded, and firms have been returned to the proprietor's account.
business.firm.disband.broadcast={prefix} {secbegin}{firm}{secend} has been disbanded by {secbegin}{sender}{secend}.
```

 This includes the ability to create an arbitrary number of static placeholders within the `placeholder` namespace, which will automatically be replaced, such as `{prefix}` below defined as `placeholder.prefix`
```java
    Message message;
    
    @Route("disband <firm>")
    @Permission("business.disband")
    @Description("Disband a firm you own")
    public void disband(@Sender Player sender, @Arg("firm") String firm) {
        Firm f = firms.getFirmByNameOrId(firm);
        if (!firms.isProprietor(firm, sender.getUniqueId())) {
            throw new NoPermissionException("You are not the proprietor of " + firm);
        }
    
        firms.disbandFirm(firm, sender.getUniqueId());
        message.send(sender, "business.firm.disband.success", "firm", firm);
        message.broadcast("business.firm.disband.broadcast", "firm", firm, "sender", sender.getName());
    }
```

A sender has to be either a CommandSender (e.g., Player), a UUID, or a custom class which implements the `HiberniaPlayer` interface (resolved by UUID). This is then followed by the key-value pairs as varargs, where the first value is the placeholder you wish to replace, and the second is the value to put to that placeholder. 
This is done in the above example to fill in the firm name in this business/company plugin.

**Placeholder values are inert by default**: MiniMessage tags inside a supplied value are escaped (shown literally) and braces in a value never trigger further placeholder expansion, so player-controlled strings can't inject markup or clickable components into your messages. When you deliberately want markup in a value — say a pre-coloured amount — wrap it: `message.send(sender, "key", "amount", Message.rich("<green>$1,000</green>"))`. Only do that for values the operator (not the player) controls.

### 4. Dialogs (Usher)

`usher` is to Paper's [Dialog API](https://docs.papermc.io/paper/dev/dialogs/) what `commander` is to Brigadier: a declarative, DI-wired, annotation-driven layer that removes the boilerplate of building dialogs and reading their inputs back by hand.

A `@Dialog` handler declares `@Screen` methods (each returns a `DialogView`) and `@Action` methods (run when a button is clicked). All the screens of one handler form a single navigable flow sharing a `@Model` object:

```java
@Dialog("find")
public final class FindDialog implements DialogHandler {

    @Inject FindTaskFactory tasks;   // constructor/field injection like any handler

    @Screen   // the default "main" screen
    public DialogView main(@Model FindState state, DialogFlow flow) {
        return DialogView.multiAction("find.title")
                .toggle("fuzzy", "find.fuzzy", "opt.on", "opt.off", state.fuzzy())
                .button("find.search", "submit")     // → @Action("submit")
                .open("find.filters", "filters")      // → opens the "filters" screen
                .exit("button.close")
                .build();
    }

    @Screen("filters")
    public DialogView filters(@Model FindState state) {
        return DialogView.confirmation("find.filters.title")
                .confirm("button.save", "applyFilters")
                .deny("button.back")                  // closes (or use a @Action that calls flow.back())
                .build();
    }

    @Action("submit")
    public void submit(@Sender Player player, @Input("fuzzy") boolean fuzzy,
                       @Model FindState state, DialogFlow flow) {
        state.setFuzzy(fuzzy);                        // typed — no view.getText(...).equals("enabled")
        flow.await(tasks.find(state), Text.key("find.querying"), (results, f) -> {
            f.close();
            // … show results
        });
    }
}
```

Open a flow from a command (or anywhere) with the injected `DialogManager`:

```java
dialogManager.open(player, FindDialog.class, new FindState(item));
```

Key pieces, each the dialog-tier cousin of something `commander` already has:

- **Typed input readback** — `@Input("key") T` resolves through an `InputBinder<T>` (the analogue of `ParameterResolver`). Built-ins cover `String`, `boolean`, `int`, `long`, `float`, `double`; register custom binders (e.g. for a domain enum) via `HiberniaModule.inputBinders(...)`. An on/off `.toggle(...)` reads back as a plain `boolean`.
- **`DialogFlow`** owns navigation — `flow.open("filters")`, `flow.back()`, `flow.refresh()`, `flow.close()` — so screens stop threading `Supplier<Dialog> previous` by hand. `flow.await(future, waitText, onDone)` shows a wait-screen, runs the future off the main thread, and hands you the result back on the main thread.
- **`DialogView`** is a renderer-agnostic spec; all text is a `Message` key (or `Text.of(component)`), so dialog labels are translatable like everything else. The only class touching Paper's dialog runtime is `PaperDialogRenderer`.
- **Errors** thrown from an `@Action` (including the framework's `NotFoundException`/`ConflictException`/… propagated from your services) render to the viewer through the same `hibernia.error.*` keys as command feedback.
- **Bedrock** — bind a Floodgate-backed `BedrockSupport` via `HiberniaModule.bedrockSupport(...)`; handlers branch on `flow.isBedrockViewer()`.

Wire it up in the bootstrap module:

```java
HiberniaModule.forPlugin(this)
        .dialogs(FindDialog.class)
        .inputBinders(ShopTypeBinder.class)   // optional custom binders
        // .bedrockSupport(FloodgateBedrockSupport.class)
        .build();
// …then, after creating the injector, dialogs are shown on demand via DialogManager.open(...).
```

> Registry-backed dialog types (`dialogList`, `serverLinks`) are not yet wrapped — `usher` currently covers the dynamic `notice`/`confirmation`/`multiAction` dialogs, which is what gameplay commands use.

## Guice Glue

It's expected to use this framework in conjunction with Guice; if you are not familiar with Guice use other resources at first to get yourself acquainted with the library or the general principles of dependency injection.

`HiberniaModule` (see *Bootstrap* above) replaces the hand-written plugin/commander modules older consumers used. Your remaining modules bind only your own tiers, e.g.:

```java
public class ServicesModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(FirmService.class).to(FirmServiceImpl.class).in(Singleton.class);
        bind(FirmStaffService.class).to(FirmStaffServiceImpl.class).in(Singleton.class);
        // ... services and persistence only — the framework owns the rest
    }
}
```

If you need lower-level control (e.g. binding handlers conditionally), the underlying multibinders are plain Guice and can still be declared by hand: `Multibinder.newSetBinder(binder(), CommandHandler.class)`, `Multibinder.newSetBinder(binder(), new TypeLiteral<ParameterResolver<?>>() {})` and `Multibinder.newSetBinder(binder(), Listener.class)`.
