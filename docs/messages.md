# Messages & i18n

[← Docs index](README.md)

The `i18n` module is a templated, MiniMessage-formatted messaging system over a `messages.properties`
file, with a `Message` bean of helper methods for sending keyed messages with placeholder substitution.

> This is a single-file template system, not full per-player internationalisation: there is one
> `messages.properties` per plugin and no per-locale selection (yet).

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

## Placeholder values are inert by default

A caller-supplied value is **escaped**: MiniMessage tags in it render literally, and braces in it never
trigger further placeholder expansion. So a player-controlled string (a chat message, a name) cannot
inject markup or clickable `run_command` components into your messages.

When you deliberately want a value to carry markup — say a pre-coloured amount the *operator* controls —
wrap it in `Message.rich(...)`:

```java
message.send(sender, "shop.sold", "amount", Message.rich("<green>$1,000</green>"));
```

Only use `rich(...)` for values you trust. Never wrap raw player input.

## `HiberniaPlayer`

A small interface (`getUniqueId()`, `getCurrentName()`) for your own player abstraction.
`Message.send(HiberniaPlayer, …)` resolves the target by **UUID** (not the possibly-stale name).

---

Related: [Commands](commands.md) · [Exceptions](exceptions.md) · [Dialogs (Usher)](dialogs.md) (dialog text uses these same keys)
