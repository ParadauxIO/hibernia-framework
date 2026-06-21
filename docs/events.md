# Events

[← Docs index](README.md)

Event listeners are the other half of the entrypoint tier (alongside commands and dialogs). The
framework lets you register Bukkit listeners through DI so they take their services by constructor
injection, keeping them as thin as command handlers.

## A listener

A plain Bukkit `Listener` with an injected service:

```java
public final class JoinListener implements Listener {

    private final AccountService accounts;

    @Inject
    public JoinListener(AccountService accounts) {
        this.accounts = accounts;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        accounts.ensureAccount(event.getPlayer().getUniqueId());   // delegate to a service
    }
}
```

The listener parses the event and calls a service — no business logic, no DB access inline.

## Registering

Bind listeners with `HiberniaModule.listeners(...)`, then register them all in one call:

```java
HiberniaModule.forPlugin(this)
        .listeners(JoinListener.class, ShopSignListener.class)
        /* … */
        .build();

// after the injector exists:
injector.getInstance(ListenerManager.class).registerAll();
```

`ListenerManager.registerAll()` registers every bound `Listener` with the Bukkit plugin manager. That's
the whole API — listeners are constructed by Guice, so anything they need is just a constructor
parameter.

---

Related: [Dependency injection](dependency-injection.md) · [Commands](commands.md)
