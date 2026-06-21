# Dialogs (Usher)

[← Docs index](README.md)

`usher` is to Paper's [Dialog API](https://docs.papermc.io/paper/dev/dialogs/) what
[`commander`](commands.md) is to Brigadier: a declarative, DI-wired, annotation-driven layer. You
describe screens and what their buttons do; the framework builds the Paper dialogs, reads inputs back as
typed values, routes clicks to your methods, and manages navigation between screens.

It removes the boilerplate dialog code otherwise repeats: stringly-typed readback
(`view.getText("k").equals("enabled")`), hand-rolled on/off toggles, `Supplier<Dialog> previous`
back-stacks threaded by hand, static methods threading services through every call, and the
`Dialog.create(...)` / `ActionButton.builder(...)` ceremony.

## Mental model

- A **`@Dialog` handler** is a bean (constructor- or field-injected like any handler).
- It declares one or more **`@Screen`** methods. Each returns a `DialogView` — the description of one
  screen.
- It declares **`@Action`** methods, run when a button bound to their name is clicked.
- All the screens of one handler form a single **flow** that shares a mutable **`@Model`** object (the
  equivalent of ChestShop's `FindState`). Navigation between screens is a back-stack owned by
  `DialogFlow`.

```java
@Dialog("find")
public final class FindDialog implements DialogHandler {

    @Inject private FindService finder;

    @Screen                                    // the default "main" screen
    public DialogView main(@Model FindState state, DialogFlow flow) {
        return DialogView.multiAction("find.title")
                .bodyItem(state.item())                       // show the item
                .toggle("fuzzy", "find.fuzzy", "opt.on", "opt.off", state.fuzzy())
                .button("find.search", "submit")              // → @Action("submit")
                .button("find.filters", "toFilters")          // → @Action that saves then navigates
                .exit("button.close")
                .columns(1)
                .build();
    }

    @Screen("filters")
    public DialogView filters(@Model FindState state) {
        return DialogView.multiAction("find.filters.title")
                .option("type", Text.key("find.type"), List.of(
                        new DialogInputSpec.OptionSpec("BUY", Text.key("type.buy"), true),
                        new DialogInputSpec.OptionSpec("SELL", Text.key("type.sell"), false)))
                .button("button.save", "applyFilters")
                .exit("button.back")                          // closes; or use a @Action that calls flow.back()
                .build();
    }

    @Action("submit")
    public void submit(Player viewer, @Input("fuzzy") boolean fuzzy, @Model FindState state, DialogFlow flow) {
        state.setFuzzy(fuzzy);
        flow.await(finder.find(state), Text.key("find.querying"), (results, f) -> {
            f.close();
            // … show results (e.g. a chest GUI)
        });
    }

    @Action("toFilters")
    public void toFilters(@Input("fuzzy") boolean fuzzy, @Model FindState state, DialogFlow flow) {
        state.setFuzzy(fuzzy);     // capture main's input before leaving …
        flow.open("filters");      // … then navigate
    }

    @Action("applyFilters")
    public void applyFilters(@Input("type") ShopType type, @Model FindState state, DialogFlow flow) {
        state.setShopType(type);
        flow.back();
    }
}
```

## Opening a dialog

Inject a `DialogManager` (e.g. into a command handler) and call `open`:

```java
public final class FindCommand implements CommandHandler {
    @Inject private DialogManager dialogs;

    @Route("find")
    public void find(@Sender Player player) {
        dialogs.open(player, FindDialog.class, new FindState(player.getInventory().getItemInMainHand()));
    }
}
```

- `open(player, FindDialog.class, model)` shows the handler's default screen (`"main"`, or the only
  screen).
- `open(player, FindDialog.class, "filters", model)` shows a specific screen.

> Open dialogs on the **main server thread** (as you would any Bukkit UI). `flow.await(...)` already
> delivers its callback on the main thread, so you don't need to think about threading inside a flow.

Bind the handler in the bootstrap:

```java
HiberniaModule.forPlugin(this).dialogs(FindDialog.class) /* … */ .build();
```

## `@Screen`, `@Action` and parameter injection

`@Screen` methods return `DialogView`. `@Action` methods return `void`. Both have their parameters
injected by annotation or by type:

| Parameter | Resolved as |
|-----------|-------------|
| `@Input("key") T` | the typed input value (see below) — **`@Action` only** |
| `@Model T` | the flow's model object (or `null` if opened without one) |
| `Player` / `Audience` / `CommandSender` | the viewer |
| `DialogFlow` | the navigation flow |
| `DialogContext` | the raw response view + flow (for ad-hoc input access) |
| `Message` | the message bean |

(There's no `@Sender` here — dialogs have no positional arguments, so the viewer is injected purely by
type.)

## Building a `DialogView`

Three dialog kinds, each with a fluent builder. All text is a **message key** (or `Text.of(component)`).

### Notice — a single acknowledgement

```java
DialogView.notice("info.title")
        .body("info.body")
        .build();                     // a default close button is supplied
// or .ok("button.ok", "acknowledge") to run @Action("acknowledge")
```

### Confirmation — yes / no

```java
DialogView.confirmation("delete.title")
        .body("delete.body")
        .confirm("button.yes", "doDelete")   // @Action("doDelete")
        .deny("button.no")                   // closes; or deny("button.no", "someAction")
        .build();
```

### Multi-action — inputs and a set of buttons

```java
DialogView.multiAction("find.title")
        .toggle("fuzzy", "find.fuzzy", "opt.on", "opt.off", false)
        .button("find.search", "submit")     // @Action button
        .open("find.filters", "filters")     // navigation button → opens the "filters" screen
        .exit("button.close")                // bottom exit button (closes)
        .columns(1)
        .build();
```

### Inputs

| Builder method | Input | Reads back as |
|----------------|-------|---------------|
| `.text(key, label)` | free text field | `String` |
| `.bool(key, label, initial)` | native checkbox | `boolean` |
| `.toggle(key, label, onLabel, offLabel, initial)` | on/off dropdown (clearer than a checkbox) | `boolean` |
| `.option(key, label, options)` | single-choice dropdown | option id `String`, or an **enum** |
| `.number(key, label, min, max, step, initial)` | numeric slider | `int`/`long`/`float`/`double` |

`.toggle(...)` and several others also accept message-key strings directly (e.g.
`.toggle("fuzzy", "find.fuzzy", "opt.on", "opt.off", false)`).

### Buttons

| Builder method | Click behaviour |
|----------------|-----------------|
| `.button(label, "actionName")` | run `@Action("actionName")` |
| `.open(label, "screenName")` | navigate to another screen of this handler |
| `.exit(label)` | the bottom exit button (closes) — multiAction |
| `.confirm(label, "actionName")` / `.deny(label[, "actionName"])` | confirmation buttons |
| `ButtonSpec.close(...)` / `.back(...)` | close / pop the stack (via `.button(spec)` / `.exit(spec)`) |

## Typed inputs

The headline feature. An `@Action` declares `@Input("key") T` and the framework reads the submitted
value as `T` — no `view.getText(...)` plumbing:

```java
@Action("submit")
public void submit(@Input("fuzzy") boolean fuzzy,    // toggle → boolean
                   @Input("page") int page,          // slider → int (rounded)
                   @Input("type") ShopType type) {   // option → enum (by constant name)
    ...
}
```

Built-in bindings: `String`, `boolean`/`Boolean`, `int`, `long`, `float`, `double`, and **any enum**
(an `.option(...)` whose ids are the enum constant names binds straight to the enum, case-insensitively;
an unrecognised id yields `null`). An on/off toggle reads back as `boolean`.

### Custom input binders

For a service-resolved type, implement `InputBinder<T>` and register it with `.inputBinders(...)`:

```java
public final class ShopRefBinder implements InputBinder<ShopRef> {
    @Override public Class<ShopRef> type() { return ShopRef.class; }
    @Override public ShopRef read(DialogResponseView view, String key) {
        String id = view.getText(key);
        return id == null ? null : ShopRef.parse(id);
    }
}
```

For ad-hoc access, an `@Action` can take a `DialogContext` and read `ctx.text(key)` / `ctx.bool(key)` /
`ctx.number(key)` directly.

## Navigation (`DialogFlow`)

`DialogFlow` is injected into screens and actions and owns a back-stack:

| Method | Effect |
|--------|--------|
| `open(screen)` | push a screen and show it |
| `back()` | pop and re-show the previous screen (closes if it was the last) |
| `refresh()` | re-render the current screen (e.g. after mutating the model) |
| `close()` | dismiss the dialog |
| `await(future, waitText, onComplete)` | show a wait-screen, run the future off-thread, deliver the result on the main thread |
| `player()` / `viewer()` / `model()` / `isBedrockViewer()` / `current()` | accessors |

### Inputs are not captured by navigation buttons

A `.open(...)` (or `.exit`/close/back) button is **pure navigation** — it does *not* read the current
screen's inputs. Minecraft dialogs are independent: input values don't survive moving to another dialog
unless you read them first. So if a screen has inputs the player should keep when moving on, give the
button an `@Action` that reads the `@Input`s into the model and *then* calls `flow.open(...)`:

```java
@Action("toFilters")
public void toFilters(@Input("fuzzy") boolean fuzzy, @Model FindState state, DialogFlow flow) {
    state.setFuzzy(fuzzy);    // capture …
    flow.open("filters");     // … then navigate
}
```

### Async work with a wait-screen

`flow.await(...)` is the show-wait → run → deliver pattern in one call:

```java
flow.await(service.queryAsync(state), Text.key("find.querying"), (results, f) -> {
    f.close();
    if (results.isEmpty()) { /* message */ } else { /* show results */ }
});
```

It shows a transient notice while the `CompletableFuture` runs, then invokes your callback on the main
thread (failures render the internal-error message and log the throwable).

## Text

Dialog text is a `Text`: `Text.key("some.key", "ph", value)` (resolved through the [`Message`](messages.md)
bean, with the same escaping rules) or `Text.of(component)` for a pre-built `Component`. Builders that
take a `String` treat it as a message key. With no `Message` bean bound, a key is rendered as raw
MiniMessage so prototyping still works.

> Tip: a `Text.key(...)` placeholder value may itself be a `Component` — e.g.
> `Text.key("find.title", "item", itemStack.displayName())` renders the item's formatted name inline
> (see [Messages → placeholder value types](messages.md#placeholder-value-types)). Use `Text.of(component)`
> when the whole piece of text is a pre-built component.

## Errors

Exceptions thrown from an `@Action` — including the framework's [semantic exceptions](exceptions.md)
propagated from your services — render to the viewer through the same `hibernia.error.*` keys as command
dispatch. Unknown exceptions log a stack trace and show the generic internal-error message.

## Bedrock players

`flow.isBedrockViewer()` tells you whether the viewer is a Geyser/Floodgate player (Bedrock clients
render dialogs through Geyser's form translation, which degrades richer layouts). Bind a Floodgate-backed
`BedrockSupport` to enable detection:

```java
HiberniaModule.forPlugin(this).bedrockSupport(FloodgateBedrockSupport.class) /* … */ .build();
```

```java
public final class FloodgateBedrockSupport implements BedrockSupport {
    @Override public boolean isBedrock(Player player) {
        return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }
}
```

This is a **detection hook only** — the framework doesn't divert rendering. Branch in your handler (e.g.
send a chat summary instead of a wait-screen) where it matters.

## Not yet covered

Registry-backed dialog types — `dialogList` and `serverLinks` — are not wrapped. They require a
`RegistrySet<Dialog>` of pre-registered dialogs, a different model from the dynamic, callback-driven
dialogs here. `usher` currently covers `notice`, `confirmation` and `multiAction`, which is what
gameplay commands use. If you need a registry menu, build it directly against the Paper API.

## Design note (for testing your own handlers)

`DialogView` is a renderer-agnostic spec; the only class that touches Paper's dialog runtime is
`PaperDialogRenderer` (an injected `DialogRenderer`). That means your handler logic — what a screen
returns and what an action does with its inputs — can be unit-tested with a fake renderer, the way the
framework's own `DialogManagerTest` does, without a running server.

---

Related: [Dependency injection](dependency-injection.md) · [Messages & i18n](messages.md) · [Exceptions](exceptions.md) · [Commands](commands.md)
