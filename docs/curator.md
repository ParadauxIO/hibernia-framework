# Chest menus (Curator)

The `curator` tier provides chest inventory GUIs using Hibernia's handler, annotation, model and flow
patterns. It complements Usher: a dialog can collect search options, then open a chest menu for
visual results. Menu handlers are Guice-managed services; each opening receives a separate model.
The framework owns layout, paging, navigation, dispatch and inventory protection. The plugin owns
queries, shop models, icon templates and commands.

## Bootstrap

```java
HiberniaModule hibernia = HiberniaModule.forPlugin(this)
        .dialogs(FindDialog.class)
        .menus(FindResultsMenu.class)
        .build();
Injector injector = Guice.createInjector(hibernia, new ServicesModule());
MenuManager menus = injector.getInstance(MenuManager.class);
menus.registerAll();
// Also call menus.shutdown() from onDisable. The manager listens for plugin disable as a fallback.
```

Menu handlers implement `curator.spi.MenuHandler`. Import `curator.annotations.Screen`, `Action` and
`Model` for chest handlers; these are separate from Usher's annotations. Public screen methods
return `MenuView`; public action methods return `void`. They can accept `MenuFlow`, `Player` and
`@Model` parameters in any order. Actions can additionally accept `MenuContext`. Registration
rejects invalid signatures, duplicate/blank routes, non-public annotated methods and missing
`main` screens. An incompatible or missing model is rejected before replacing an existing menu.
Use constructor injection for services. Keep viewer state in the model, not handler fields.

```java
public final class FindResultsMenu implements MenuHandler {
    @Screen
    public MenuView results(@Model FindState state, MenuFlow flow) {
        return MenuView.chest(state.title(), 6)
                .content(0, 44, state.results().stream()
                        .map(shop -> MenuItem.button(shop.icon(), "select", shop))
                        .toList())
                .slot(45, MenuItem.button(state.backIcon(), "back", null))
                .slot(48, MenuItem.button(state.previousIcon(), "previous", null))
                .slot(49, MenuItem.display(state.queriedItem()))
                .slot(50, MenuItem.button(state.nextIcon(), "next", null))
                .fill(MenuItem.display(state.filler()))
                .build();
    }

    @Action("select")
    public void select(MenuContext click, MenuFlow flow) {
        FoundShop shop = click.payload(FoundShop.class);
        String command = click.click().isRightClick() ? shop.gpsCommand() : shop.directionsCommand();
        flow.close();
        click.player().performCommand(command);
    }

    @Action("previous") public void previous(MenuFlow flow) { flow.previousPage(); }
    @Action("next") public void next(MenuFlow flow) { flow.nextPage(); }
    @Action("back") public void back(MenuFlow flow) { flow.back(); }
}
```

`FindState` and `FoundShop` above are consumer types. For a complete compiled example, including
conditional paging buttons and async previews, see
[FindResultsMenuExample](../src/test/java/io/paradaux/hibernia/framework/curator/FindResultsMenuExample.java).
Copy that handler into your plugin and adapt its `ItemLookup` and `Icons` bindings. It is an example,
not a ChestShop dependency shipped in the framework JAR.

## Views, state and navigation

- Chests have 1–6 rows and zero-based slots. Slots `0..44` are five content rows; `45..53` are the footer.
- `slot` places a fixed icon. `content(first, last, entries)` reserves one inclusive page area.
  Overlapping slots are rejected. `fill` decorates unreserved slots, so call it last.
- `MenuItem.display` creates a protected display item. `button` associates an action and payload.
  ItemStacks are copied on input and output; all metadata, lore, custom components and glint survive.
  Payloads are application objects: treat them as immutable or scope them to the viewer.
- `MenuView` snapshots the builder and entry list. `items(page)` clamps out-of-range page indexes.
  Empty results have one empty page. `flow.refresh()` rebuilds from the model and clamps the page
  after the result count shrinks. When building conditional paging controls from changing result sets,
  reset the page with `flow.page(0)` after updating the model.
- `flow.open("preview")` pushes a screen. `back()` restores its parent's page, or closes at the root.
  `backOnClose(true)` returns on player escape. Opening an unrelated inventory, disconnecting or
  disabling the plugin never forces the old menu back open. `close()` always ends the whole journey.
- Resolve localized Adventure titles and icon labels using the plugin's injected `Message` service.
  Slot contents can be built with Paper's ItemStack/meta APIs or existing configuration templates.

All menu methods must run on the server thread. Handlers run on the next server tick, outside the
inventory transaction. Every click/drag in a managed view is cancelled, including bottom-inventory
shift transfers, hotbar/offhand swaps, drops, double-click collection and creative cloning. Only
left/right clicks and their shift variants in actionable top slots dispatch. Duplicate pending clicks
are coalesced; queued clicks for replaced screens are ignored. These menus are read-only UIs, not
editable containers.

## Async search and preview

```java
flow.await(finder.findAsync(state), loadingView, (results, current) -> {
    state.setResults(results);
    current.open("results");
});
```

Start database work in the service's executor; `await` accepts an already-started `CompletionStage`.
It displays a transient loading view and delivers the completion on the server thread. Item
creation/deserialization requiring server access should also happen on that thread. New requests,
navigation, refresh, close, replacement, disconnect and shutdown invalidate old completions. A loading
screen preserves the underlying page. Domain exceptions use the same localized error mapping as
commands; unexpected failures are logged with generic player feedback and the flow closes.

To connect the existing Usher `/find` search, inject `MenuManager` in its handler and, in the
`DialogFlow.await` success callback, close the dialog and open the results menu:

```java
flow.await(finder.find(state), Text.key("find.querying"), (results, dialog) -> {
    Player player = dialog.player();
    dialog.close();
    menus.open(player, FindResultsMenu.class, createResultsState(results, state));
});
```

The consumer should preserve its existing empty-result message, permissions and request lifecycle.
This change adds framework capability; it does not change Hibernia Economy's `/find` registration or
replace its existing dialog implementation automatically.

## ChestShopDatabase parity

Reference: [MCCitiesNetwork/chestshop-database](https://github.com/MCCitiesNetwork/chestshop-database),
commit `c7cd31f135e89f4e11a43c14cc6999b1f99ed3ce`. The local search found deployed JAR/configuration
copies, but no source checkout. The upstream `gui/ShopResultsGUI.java`, `gui/dialog/*`, `FindState`
and command source were inspected through GitHub.

The original uses dialogs for search/filter/sort options and a chest for results. Its GUI capabilities
map as follows:

| Original behavior | Hibernia equivalent |
| --- | --- |
| Six-row results chest; 45 shop icons per page | `MenuView.chest(title, 6).content(0, 44, items)` |
| Fixed gray footer; back at 45, previous at 48, queried item at 49, next at 50 | Fixed slots and `fill`; compiled example hides unavailable paging buttons |
| Buy/sell/both icon templates, owner, bundle/unit prices, quantity, stock, capacity, distance, region and coordinates | Consumer builds template ItemStacks with its existing services/config/Message; the menu preserves all metadata |
| Similar-item glint | Consumer sets `ENCHANTMENT_GLINT_OVERRIDE` on matching rows' icons |
| Left and shift-left directions; right and shift-right GPS | `MenuContext.click()` and typed shop payload; example executes pre-resolved player commands |
| No configured right command → item preview | Example uses `ItemLookup`, per-search cache and `flow.await` |
| Three-row preview, item at 13, back at 18, gray filler | Example's `preview` screen |
| Preview back/escape restores results | `flow.back()` and `backOnClose(true)`, preserving page |
| Optional nested results parent | Use a parent screen in the same flow and `backOnClose(true)` on results |
| Main-hand/item-code lookup, fuzzy toggle, quick search presets | Existing command/service and Usher model; pass resulting rows to the menu |
| Buy/sell/both filters, hide empty/full, weighted ascending/descending sorting | Existing service/model and Usher inputs; chest actions can also mutate those models and refresh |
| Async query wait/errors/empty results | `await` plus consumer empty-result feedback; shared framework exception rendering |

The framework intentionally requires explicit action routing. Number keys, drops and double-clicks
do not execute the original GUI's catch-all left-command fallback. This preserves direction/GPS
controls without triggering commands from inventory-transfer gestures.

## Item safety and verification

Curator inventories carry a private holder identifying the owning manager. Click and drag protection
follows that inventory, even if the viewer's active session has ended. Opening a retired display or
another player's display is rejected. Closing, replacing, disconnecting or shutting down clears
copied display icons before releasing the inventory. Existing player inventory and cursor stacks
remain subject to the server's normal close behavior; the framework never grants display items.

The `MenuInventorySafetyTest` integration suite uses MockBukkit's registered event dispatcher with
real Bukkit event objects, in-memory inventories and ItemStack metadata. It checks:

- Every `InventoryAction` crossed with every `ClickType` at actionable, decorative, empty, bottom,
  hotbar and outside slots, including bundle transfer actions, swaps, collection and drops.
- Both drag modes across top, bottom and mixed slots, and creative inventory events.
- All inventory-close reasons, disconnect, shutdown, cancelled refreshes and stale async results.
- Foreign viewers, retired references, listeners that close or replace screens mid-event, and
  independence between menu managers belonging to different plugins.
- Icon copies do not alias input templates, and denied events leave display, player and cursor state
  unchanged. Unsupported MockBukkit test operations fail rather than silently skip safety checks.

These tests reproduced five ownership/retirement failures before the repair. In an isolated source
copy, removing click cancellation makes all 25 action cases fail on `DEFAULT` versus expected `DENY`.
The existing tests additionally cover paging, preview caching, navigation, registration and Guice.

The event tests verify Paper's cancellation contract; MockBukkit does **not** execute vanilla packet
processing or its complete item-transfer algorithm. They are not proof against every Minecraft bug,
client exploit, or another plugin deliberately undoing cancellation or directly copying items.
Live Paper/Geyser packet-level verification with the deployed plugin set remains a separate check.
No database, direction plugin or running server was modified here.
