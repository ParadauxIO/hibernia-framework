# Exceptions

[← Docs index](README.md)

The framework ships a small set of unchecked, HTTP-semantic exceptions. The intended pattern is to
**throw them from your service layer and let them propagate out of the command or dialog handler** — the
framework catches them and renders a message to the player. You only catch one yourself when a handler
wants custom behaviour.

## The taxonomy

| Exception | Semantics (HTTP analogue) | When to throw |
|-----------|---------------------------|---------------|
| `BadCommandException` | 400 | input is syntactically valid but semantically wrong |
| `NoPermissionException` | 403 | a permission check fails in your service layer |
| `NotFoundException` | 404 | a referenced resource doesn't exist |
| `ConflictException` | 409 | violates a uniqueness/primary-key constraint |
| `ExceedsLimitException` | 429-ish | a cooldown or value limit is exceeded |
| `InternalException` | 500 | an unexpected internal failure |

All extend `RuntimeException`, so they don't pollute signatures.

```java
public BigDecimal balanceOf(UUID who) {
    return accounts.find(who)
            .orElseThrow(() -> new NotFoundException("No account for that player"))
            .balance();
}
```

A command/dialog handler that calls this needs no try/catch: if the account is missing, the player sees
the not-found message and the handler body stops.

## How they render

Both `CommandManager` (command dispatch) and `DialogManager` (dialog `@Action` clicks) map each
exception type to a message key. If a `Message` bean is bound, the key is looked up in
`messages.properties`; otherwise a built-in MiniMessage default is used. The `{message}` placeholder
carries the exception's message text.

| Exception / case | Message key | Default |
|------------------|-------------|---------|
| `NoPermissionException` (and `@Permission` denial) | `hibernia.error.no-permission` | generic denial |
| `BadCommandException` | `hibernia.error.bad-command` | `{message}`, red |
| `NotFoundException` | `hibernia.error.not-found` | `{message}`, red |
| `ConflictException` | `hibernia.error.conflict` | `{message}`, red |
| `ExceedsLimitException` | `hibernia.error.exceeds-limit` | `{message}`, red |
| invalid / unresolvable argument | `hibernia.error.invalid-argument` | explanation, red |
| anything else / `InternalException` | `hibernia.error.internal` | generic message; **stack trace logged, never shown to the player** |

The keys are also available as constants on `CommandManager` (`KEY_NO_PERMISSION`, `KEY_NOT_FOUND`, …).

## Overriding the messages

Add the keys to your `messages.properties` to re-word or translate them. The `{message}` placeholder is
the exception text:

```properties
hibernia.error.no-permission={prefix} <red>You can't do that.</red>
hibernia.error.not-found={prefix} <red>{message}</red>
hibernia.error.internal={prefix} <red>Something went wrong — an admin has been notified.</red>
```

Because exception messages flow through the [escaping rules](messages.md#placeholder-values-are-inert-by-default),
they're safe to surface even when they incorporate user-derived text.

---

Related: [Commands](commands.md) · [Dialogs (Usher)](dialogs.md) · [Messages & i18n](messages.md)
