# Commands (`commander`)

[← Docs index](README.md)

`commander` builds a Brigadier command tree from annotated handler classes and registers it with Paper.
You write thin, typed handler methods; the framework handles parsing, argument resolution, tab
completion, permissions, async dispatch and error rendering.

## A handler

```java
@Command({"eco", "economy"})              // one or more root labels
public final class EconomyCommands implements CommandHandler {

    @Inject private AccountService accounts;   // field injection is allowed in @Command classes

    @Route("")                            // /eco
    public void root(@Sender Player sender) { ... }

    @Route("balance [player]")            // /eco balance   or   /eco balance <player>
    public void balance(@Sender Player sender,
                        @OptionalArg(value = "player", defaultValue = OptionalArg.SENDER) OfflinePlayer who) { ... }

    @Route("give <player> <amount>")      // /eco give <player> <amount>
    @Permission("eco.give")
    public void give(@Sender Player sender,
                     @Arg("player") OfflinePlayer target,
                     @Arg("amount") BigDecimal amount) {
        accounts.transfer(sender, target, amount);   // throws semantic exceptions; framework renders them
    }
}
```

Bind it and register:

```java
HiberniaModule.forPlugin(this).handlers(EconomyCommands.class) /* … */ .build();
// after the injector exists:
injector.getInstance(CommandManager.class).registerAll();
```

## Route syntax

A `@Route` value is a space-separated pattern:

| Token | Meaning |
|-------|---------|
| `balance` | a literal sub-command |
| `<name>` | a **required** argument bound to an `@Arg("name")` parameter |
| `[name]` | an **optional** argument bound to an `@OptionalArg("name")` parameter |
| `""` (empty) | the **root** route (`/eco` with no sub-command) |

Optional `[...]` segments must form the **tail** of the route — the command is executable both with and
without them. A method may carry several `@Route` annotations (route aliases).

## Parameters

Every parameter must be annotated, or be a recognised sender type:

- **`@Sender`** — injects the command sender. The runtime sender must be assignable to the parameter
  type (`Player`, `CommandSender`, `ConsoleCommandSender`, …) or the command is rejected.
- **`@Arg("name")`** — a required argument; `name` must appear as `<name>` in the route.
- **`@OptionalArg(value = "name", defaultValue = "…")`** — an optional argument; `name` appears as
  `[name]`. When omitted the default applies. `OptionalArg.SENDER` defaults to the command sender (e.g.
  an `OfflinePlayer` defaulting to the player who ran it). An empty default yields `null` for reference
  types — a **primitive** optional therefore *requires* a `defaultValue`.
- **`@GreedyArg("name")`** — captures all remaining input (spaces included). Must be the last argument.
  Useful for messages or URLs.

`@Arg`, `@OptionalArg` and `@GreedyArg` take a `sanitize` flag (default `true`) that strips MiniMessage
tags and risky punctuation from `String` values. Set `sanitize = false` for arguments that legitimately
contain such characters (e.g. a URL).

## Argument resolution

Argument tokens are turned into typed values by `ParameterResolver`s. Built-ins:

| Type | Notes |
|------|-------|
| `String` | sanitized unless `sanitize = false` |
| `Integer` / `int`, `Long` / `long` | native Brigadier integer/long arguments |
| `BigDecimal` | accepts thousands-separator commas — `/pay X 1,000` works (see below) |
| `Boolean` / `boolean` | accepts `true/false/yes/no/y/n/1/0/on/off`; tab-completes `true`/`false` |
| `OfflinePlayer` | resolves from the **local player cache only** (Floodgate/Bedrock-safe — never blocks on Mojang, never fabricates a UUID) |

Primitive-typed parameters transparently fall back to the boxed resolver (`boolean flag` works like
`Boolean flag`).

### Custom resolvers

Implement `ParameterResolver<T>` and register it with `.resolvers(...)`:

```java
@Singleton
public final class FirmPlayerResolver implements ParameterResolver<FirmPlayer> {
    private final FirmPlayerService players;
    @Inject public FirmPlayerResolver(FirmPlayerService players) { this.players = players; }

    @Override public Class<FirmPlayer> type() { return FirmPlayer.class; }

    @Override public Optional<FirmPlayer> resolve(String token, CommandSender sender) {
        return players.findByName(token);     // empty() ⇒ "invalid argument", handler not invoked
    }

    @Override public List<String> suggestions(String prefix, CommandSender sender) {
        return players.searchByPrefix(prefix, 20).stream().map(FirmPlayer::getCurrentName).toList();
    }
}
```

**Threading contract:** `suggestions(...)` is always called off the main thread, and `resolve(...)`
runs off the main thread for `@Async` routes. Back resolvers with service-managed caches, not live
world/entity access.

### The `BigDecimal` argument type

Brigadier's unquoted-word reader rejects `,`, so `1,000` would silently truncate to `1`. `commander`
binds `BigDecimal` parameters to a custom argument type that reads the whole token; the resolver strips
the comma before parsing. This is why monetary commands accept `/pay X 1,000`.

## Permissions

`@Permission("node")` on a class gates every route; on a method it overrides the class node for that
route. A shared root visible to a sender who holds *any* of the contributing classes' permissions; a
class with no `@Permission` leaves the root open and per-route permissions are still enforced.

## Async

`@Async` on a route method dispatches it off the main thread. Sender feedback and other main-thread work
are scheduled back automatically. The annotated method must be thread-safe and avoid main-thread-only
Bukkit calls unless it hops back itself.

## Help / descriptions

- `@Command(description = "…")` is passed to Paper's command registrar and shows in `/help`.
- `@Description("…")` on a route is recorded in `CommandManager.routeIndex()` — a
  `List<RouteInfo>` (root, pattern, description, permission, async) you can use to generate custom help
  output instead of hand-maintaining a parallel list.

## Registration-time validation

Routes are validated when commands register, not when first run. A handler that fails — an unmatched
placeholder, a required `@Arg` missing from the route, a literal after an optional segment, a greedy
arg that isn't last, two routes binding the same path, conflicting argument types at the same position —
is **skipped and logged**; the plugin's other commands still register. (Previously these mistakes
silently dropped a route from the tree.)

## Errors

When a service throws one of the framework's [exceptions](exceptions.md) out of a handler, the framework
renders it to the sender. See that page for the message keys.

---

Related: [Messages & i18n](messages.md) · [Exceptions](exceptions.md) · [Dependency injection](dependency-injection.md)
