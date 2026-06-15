# Messages & i18n

[← Docs index](README.md)

The `i18n` module is a templated, MiniMessage-formatted, **locale-aware** messaging system over
`messages.properties` files, with a `Message` bean of helper methods for sending keyed messages with
placeholder substitution. Each message is rendered in the recipient's own locale; a plugin that ships
only one file behaves exactly as a single-locale plugin.

See [Locales](#locales) for translations. The rest of this section uses the base file.

## `messages.properties`

Ship a default `messages.properties` in your plugin's resources. The framework copies it into the data
folder on first run (never overwriting an operator-edited file).

```properties
# Static placeholders. Anything under "placeholder." can be used in any message below.
placeholder.prefix=<bold><blue>BUSINESS</blue></bold> <gray>»</gray>
placeholder.secbegin=<#6f6fff>
placeholder.secend=</#6f6fff>

business.general.no-permission={prefix} You do not have permission to run this command.
business.firm.disband.success={prefix} {firm} has been disbanded.
business.firm.disband.broadcast={prefix} {secbegin}{firm}{secend} disbanded by {secbegin}{sender}{secend}.
```

- Values are [MiniMessage](https://docs.advntr.dev/minimessage/format.html) — `<bold>`, `<red>`,
  `<#hex>`, gradients, etc.
- `{name}` is a placeholder. Resolution order: **caller-supplied values → `<namespace>.placeholder.*`
  → global `placeholder.*`**, expanded recursively (bounded), so placeholders can reference each other.
- A namespace is the part of a key before the first dot (`business` above), so
  `business.placeholder.x` is a placeholder visible only to `business.*` keys.
- Escape literal braces by doubling: `{{` and `}}`.

## Sending messages

Inject `Message` (bound by `HiberniaModule` unless `.withoutMessages()`):

```java
public final class FirmCommands implements CommandHandler {
    @Inject private Message message;

    @Route("disband <firm>")
    @Permission("business.disband")
    public void disband(@Sender Player sender, @Arg("firm") String firm) {
        firms.disband(firm, sender.getUniqueId());   // throws semantic exceptions if it can't
        message.send(sender, "business.firm.disband.success", "firm", firm);
        message.broadcast("business.firm.disband.broadcast", "firm", firm, "sender", sender.getName());
    }
}
```

Placeholder values are passed as alternating `key, value` varargs.

### API

| Method | Purpose |
|--------|---------|
| `send(CommandSender to, String key, Object... kv)` | send to a sender |
| `send(UUID to, String key, Object... kv)` | send to an online player by UUID (no-op if offline) |
| `send(HiberniaPlayer to, String key, Object... kv)` | send to a custom player type (resolved by UUID) |
| `send(Collection<? extends CommandSender>, String key, Object... kv)` | send to many |
| `broadcast(String key, Object... kv)` | all online players + console |
| `component(String key, Object... kv)` / `component(String key, Map<String,?>)` | build a `Component` |
| `format(String key, Object... kv)` | the resolved string (pre-deserialize) |
| `componentOr(String key, String fallbackPattern, …)` | render the key, or a fallback MiniMessage pattern if the key is absent |
| `reload()` | re-read `messages.properties` from disk |

## Placeholder value types

`component(...)`, `componentOr(...)` and `send(...)` bind each placeholder value as a MiniMessage tag
resolver, so the **type** of the value you pass decides how it renders:

| Value type | Renders as | Use for |
|------------|-----------|---------|
| any plain value (`String`, number, …) | **inert literal text** (tags escaped, braces not expanded) | the default — safe for player-controlled input |
| a `Component` / `ComponentLike` | that component, **styling intact** | a formatted item name, a coloured player name, a clickable element |
| `Message.rich(String)` | **trusted MiniMessage markup** | operator-controlled markup (e.g. a pre-coloured amount) |

So a player-controlled string (a chat message, a name) can never inject markup or a clickable
`run_command` into your messages, while a `Component` you build yourself renders properly:

```java
// inert: any tags in the player's name render literally
message.send(sender, "chat", "name", player.getName());

// component: the item's formatted display name keeps its colour/style
message.send(sender, "shop.bought", "item", itemStack.displayName());

// trusted markup (operator value only — never wrap raw player input)
message.send(sender, "shop.sold", "amount", Message.rich("<green>$1,000</green>"));
```

> `format(...)` returns a `String` and is the exception: it can't hold a component, so a `ComponentLike`
> value is rendered via `toString()` there. Use `component(...)`/`send(...)` whenever a value is a component.

## Locales

`messages.properties` is the **base bundle**. Translations are sibling files named with a locale suffix
(the [`ResourceBundle`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ResourceBundle.html)
convention):

```
plugins/MyPlugin/
├── messages.properties        # base / default
├── messages_ga.properties     # Irish
├── messages_fr.properties     # French
└── messages_pt_BR.properties  # Brazilian Portuguese
```

A message is rendered in the **recipient's client locale** (`Player.locale()`). Console and other
non-player senders use the configured **default locale** (`Locale.ROOT` — the base file — unless you set
one):

```java
message.defaultLocale(Locale.of("ga"));   // optional; e.g. read from config
```

**Fallback is per key.** Lookup walks `lang_COUNTRY → lang → default-locale → base` and uses the first
bundle that defines the key (or placeholder). So a translator only overrides the keys they've actually
translated — everything else falls through to the base text. A plugin shipping only
`messages.properties` resolves every locale to that file.

```properties
# messages.properties
greeting=Hello, {name}!
only.english=This line isn't translated anywhere.
```
```properties
# messages_ga.properties  (only translates what it needs)
greeting=Dia duit, {name}!
```

```java
message.send(player, "greeting", "name", player.getName());
// Irish client → "Dia duit, Alex!"   English/other client → "Hello, Alex!"
// `only.english` resolves to the base text for every locale.
```

`broadcast(...)` and `send(Collection, …)` render **per recipient**, so a single broadcast reaches each
player in their own language. `Message.availableLocales()` returns the locales a bundle file was found
for. Command and dialog error messages (`hibernia.error.*`) are localized to the sender too.

> The framework selects the locale; **you** supply the translated files. This module still uses
> `.properties` (not YAML) and the `{name}` placeholder syntax.

## `HiberniaPlayer`

A small interface (`getUniqueId()`, `getCurrentName()`) for your own player abstraction.
`Message.send(HiberniaPlayer, …)` resolves the target by **UUID** (not the possibly-stale name).

---

Related: [Commands](commands.md) · [Exceptions](exceptions.md) · [Dialogs (Usher)](dialogs.md) (dialog text uses these same keys)
