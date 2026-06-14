# Dependency injection

[← Docs index](README.md)

The framework is built on [Google Guice](https://github.com/google/guice) 7. Guice is exposed as an
`api` dependency because the framework's own surface uses Guice types (`HiberniaModule extends
AbstractModule`, `CommandManager` takes an `Injector`). If you're new to Guice, read its wiki first —
this page assumes you know what a module and `@Inject` are.

## `HiberniaModule`

`HiberniaModule` is the framework-owned module. It collapses what used to be three hand-written modules
(plugin bindings, a commander module, a configuration-component loop) into one builder.

```java
HiberniaModule hibernia = HiberniaModule.forPlugin(this)
        .scanConfiguration("net.example.myplugin.model.config")   // repeatable
        .handlers(EconomyCommands.class, AdminCommands.class)      // @Command classes
        .resolvers(FirmPlayerResolver.class)                       // custom ParameterResolvers
        .listeners(JoinListener.class)                             // Bukkit Listeners
        .dialogs(FindDialog.class)                                 // @Dialog handlers
        .inputBinders(ShopTypeBinder.class)                        // custom dialog InputBinders
        .bedrockSupport(FloodgateBedrockSupport.class)             // optional
        .withoutMessages()                                         // optional, see below
        .build();
```

### What it binds

| Binding | Notes |
|---------|-------|
| `JavaPlugin` and `Plugin` | the running plugin instance |
| `ConfigurationLoader` | constructed and the configured packages scanned eagerly at `build()` |
| every `@ConfigurationComponent` | each discovered component, bound as a singleton instance |
| `Message` | eager singleton, unless `.withoutMessages()` |
| `Set<CommandHandler>` | from `.handlers(...)` |
| `Set<ParameterResolver<?>>` | from `.resolvers(...)` |
| `Set<Listener>` | from `.listeners(...)` |
| `Set<DialogHandler>` | from `.dialogs(...)` |
| `Set<InputBinder<?>>` | from `.inputBinders(...)` |
| `DialogRenderer` | bound to `PaperDialogRenderer` |
| `BedrockSupport` | only if `.bedrockSupport(...)` is set; otherwise everyone is treated as Java |

The multibinder sets are always created (even when empty), so `CommandManager`, `ListenerManager` and
`DialogManager` are always injectable.

### `.withoutMessages()`

`Message` eagerly saves a bundled `messages.properties` from your jar on first construction. If your
plugin doesn't ship that resource, call `.withoutMessages()` so the framework doesn't try. Command and
dialog error feedback then falls back to built-in MiniMessage defaults instead of message keys.

### Typed config before the injector

`scanConfiguration(...)` runs at `build()`, so loaded `@ConfigurationComponent` instances are available
*before* you create the injector — useful when another module needs settings to construct itself:

```java
DatabaseConfiguration db = hibernia.configuration(DatabaseConfiguration.class);
Injector injector = Guice.createInjector(hibernia, new DatabaseModule(db), new ServicesModule());
```

`configuration(Class)` throws `IllegalStateException` if no such component was loaded (so a typo in the
scanned package fails loudly rather than handing you `null`).

## Your modules

`HiberniaModule` binds the framework's surface; your modules bind only your tiers:

```java
public final class ServicesModule extends AbstractModule {
    @Override protected void configure() {
        bind(FirmService.class).to(FirmServiceImpl.class).in(Singleton.class);
        bind(FirmStaffService.class).to(FirmStaffServiceImpl.class).in(Singleton.class);
        // services and persistence only — the framework owns the rest
    }
}
```

## Registering entrypoints

After the injector exists, ask it for the managers and register:

```java
injector.getInstance(CommandManager.class).registerAll();   // hooks Paper's COMMANDS lifecycle
injector.getInstance(ListenerManager.class).registerAll();  // registers every bound Listener
// DialogManager needs no registration — inject it and call open(...) on demand.
```

## Injection style

- **Services and listeners** use **constructor injection** (`@Inject` constructor).
- **`@Command` handler classes** are the one exception: the command framework constructs them and
  permits **`@Inject` field injection** inside them. (You still *can* use constructor injection if the
  handler is Guice-instantiated via the multibinder, which it is — both work.)
- **`@Dialog` handler classes** are Guice-instantiated too, so use constructor or field injection
  freely. To open dialogs from a command, inject a `DialogManager`.

## Wiring by hand

If you need finer control than the builder offers (e.g. conditional bindings, custom scopes), the
underlying multibinders are plain Guice:

```java
Multibinder.newSetBinder(binder(), CommandHandler.class).addBinding().to(EconomyCommands.class);
Multibinder.newSetBinder(binder(), new TypeLiteral<ParameterResolver<?>>() {}).addBinding().to(FirmPlayerResolver.class);
Multibinder.newSetBinder(binder(), Listener.class).addBinding().to(JoinListener.class);
Multibinder.newSetBinder(binder(), DialogHandler.class).addBinding().to(FindDialog.class);
Multibinder.newSetBinder(binder(), new TypeLiteral<InputBinder<?>>() {}).addBinding().to(ShopTypeBinder.class);
bind(DialogRenderer.class).to(PaperDialogRenderer.class);
```

`HiberniaModule` is just a convenience over these; mixing the two is fine.
