package io.paradaux.hibernia.framework.curator;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import io.paradaux.hibernia.framework.commander.ErrorRenderer;
import io.paradaux.hibernia.framework.curator.annotations.Action;
import io.paradaux.hibernia.framework.curator.annotations.Model;
import io.paradaux.hibernia.framework.curator.annotations.Screen;
import io.paradaux.hibernia.framework.curator.spi.MenuHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Guice-managed chest menus. Call registerAll during enable and shutdown during disable.
 * Menus are display-only: every click and drag in a managed view is cancelled, including the player's inventory.
 * Only left/right clicks (including shift variants) on actionable top slots invoke handlers, on the next tick.
 */
@Singleton
public final class MenuManager implements Listener {
    private final JavaPlugin plugin;
    private final Set<MenuHandler> handlers;
    private final ErrorRenderer errors;
    private final Map<Class<?>, Definition> definitions = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private boolean registered;

    /** Creates a manager from the framework's menu multibinder. */
    @Inject
    public MenuManager(JavaPlugin plugin, Set<MenuHandler> handlers, Injector injector) {
        this.plugin = plugin;
        this.handlers = Set.copyOf(handlers);
        errors = new ErrorRenderer(plugin, injector);
    }

    /** Validates all handler signatures and registers inventory listeners once. */
    public void registerAll() {
        requireMain();
        if (registered) return;
        Map<Class<?>, Definition> validated = new HashMap<>();
        for (MenuHandler handler : handlers) validated.put(handler.getClass(), index(handler));
        definitions.putAll(validated);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registered = true;
    }

    /** Opens the main screen with a per-player model, replacing any earlier menu journey. */
    public MenuFlow open(Player player, Class<? extends MenuHandler> handler, Object model) {
        requireMain();
        if (!registered) throw new IllegalStateException("Call registerAll first");
        requireScreen(handler, "main");
        Definition definition = definitions.get(handler);
        for (Method method : definition.screens().values()) validateModel(method, model);
        for (Method method : definition.actions().values()) validateModel(method, model);
        Session previous = sessions.get(player.getUniqueId());
        if (previous != null) close(previous.flow());
        MenuFlow flow = new MenuFlow(this, player, handler, model);
        flow.open("main");
        return flow;
    }

    /** Invalidates pending callbacks, closes owned views and unregisters listeners. Safe to call repeatedly. */
    public void shutdown() {
        requireMain();
        for (Session session : java.util.List.copyOf(sessions.values())) close(session.flow());
        HandlerList.unregisterAll(this);
        registered = false;
        definitions.clear();
    }

    /** Cancels all item transfers and defers valid actions until the inventory transaction has completed. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        Session session = session(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory());
        if (session == null) return;
        event.setCancelled(true);
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        MenuItem item = session.items().get(event.getRawSlot());
        if (item == null || item.action() == null || session.pending()) return;
        session.pending(true);
        long revision = session.flow().revision();
        later(() -> {
            if (sessions.get(session.flow().player().getUniqueId()) != session
                    || revision != session.flow().revision()) return;
            session.pending(false);
            try {
                invoke(definitions.get(session.flow().handler()).actions().get(item.action()), session.flow(),
                        new MenuContext(session.flow(), event.getRawSlot(), event.getClick(), item.payload()));
            } catch (Exception ex) { failure(session.flow(), ex); }
        });
    }

    /** Blocks drag distribution into or across a managed inventory. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (session(event.getWhoClicked().getUniqueId(), event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    /** Handles escape-to-parent while ensuring replacement inventories cannot revive old screens. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Session session = session(event.getPlayer().getUniqueId(), event.getInventory());
        if (session == null) return;
        if (event.getReason() == InventoryCloseEvent.Reason.PLAYER && session.flow().view().backOnClose()) {
            session.flow().cancelPending();
            long revision = session.flow().revision();
            later(() -> {
                if (sessions.get(event.getPlayer().getUniqueId()) == session
                        && session.flow().revision() == revision) {
                    session.flow().back();
                }
            });
        } else {
            sessions.remove(event.getPlayer().getUniqueId());
            session.flow().invalidate();
        }
    }

    /** An external inventory opened during a deferred return takes precedence over this journey. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.inventory() != event.getInventory()) {
            sessions.remove(event.getPlayer().getUniqueId());
            session.flow().invalidate();
        }
    }

    /** Removes disconnected viewers and invalidates unfinished work. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) session.flow().invalidate();
    }

    /** Cleans up when the owning plugin is disabled. */
    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) shutdown();
    }

    void requireMain() {
        if (!plugin.getServer().isPrimaryThread()) throw new IllegalStateException("Menus require the server thread");
    }

    void requireScreen(Class<?> handler, String name) {
        Definition definition = definitions.get(handler);
        if (definition == null || !definition.screens().containsKey(name)) {
            throw new IllegalArgumentException("Unknown menu screen: " + handler.getName() + "/" + name);
        }
    }

    void render(MenuFlow flow) {
        try {
            show(flow, (MenuView) invoke(definitions.get(flow.handler()).screens().get(flow.current()), flow, null), false);
        } catch (Exception ex) { failure(flow, ex); }
    }

    void show(MenuFlow flow, MenuView view, boolean preservePage) {
        Objects.requireNonNull(view, "Screen returned null");
        Map<Integer, MenuItem> items = view.items(flow.page());
        for (MenuItem item : items.values()) {
            if (item.action() != null && !definitions.get(flow.handler()).actions().containsKey(item.action())) {
                throw new IllegalArgumentException("Unknown menu action: " + item.action());
            }
        }
        Inventory inventory = plugin.getServer().createInventory(null, view.size(), view.title());
        items.forEach((slot, item) -> inventory.setItem(slot, item.icon()));
        flow.shown(view, preservePage);
        Session next = new Session(flow, inventory, items);
        Session previous = sessions.put(flow.player().getUniqueId(), next);
        try {
            flow.player().openInventory(inventory);
        } catch (RuntimeException ex) {
            restorePreviousIfOpen(next, previous);
            throw ex;
        }
        // InventoryOpenEvent may have been cancelled or redirected by another plugin.
        if (flow.player().getOpenInventory().getTopInventory() != inventory) {
            restorePreviousIfOpen(next, previous);
            close(flow);
        }
    }

    void close(MenuFlow flow) {
        requireMain();
        Session session = sessions.get(flow.player().getUniqueId());
        flow.invalidate();
        if (session == null || session.flow() != flow) return;
        sessions.remove(flow.player().getUniqueId());
        if (flow.player().getOpenInventory().getTopInventory() == session.inventory()) flow.player().closeInventory();
    }

    void later(Runnable action) {
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, action);
    }

    void failure(MenuFlow flow, Throwable error) {
        Throwable cause = error;
        while ((cause instanceof InvocationTargetException || cause instanceof java.util.concurrent.CompletionException)
                && cause.getCause() != null) cause = cause.getCause();
        errors.handleInvocationFailure(flow.player(), cause, "menu " + flow.handler().getName());
        close(flow);
    }

    private void restorePreviousIfOpen(Session next, Session previous) {
        Player player = next.flow().player();
        if (sessions.get(player.getUniqueId()) == next && previous != null
                && player.getOpenInventory().getTopInventory() == previous.inventory()) {
            sessions.put(player.getUniqueId(), previous);
        }
    }

    private Session session(UUID viewer, Inventory inventory) {
        Session session = sessions.get(viewer);
        return session != null && session.inventory() == inventory ? session : null;
    }

    private Object invoke(Method method, MenuFlow flow, MenuContext context) throws ReflectiveOperationException {
        Object[] args = new Object[method.getParameterCount()];
        var params = method.getParameters();
        for (int i = 0; i < params.length; i++) {
            if (params[i].isAnnotationPresent(Model.class)) args[i] = flow.model();
            else if (params[i].getType() == MenuFlow.class) args[i] = flow;
            else if (params[i].getType() == Player.class) args[i] = flow.player();
            else args[i] = context;
        }
        return method.invoke(definitions.get(flow.handler()).handler(), args);
    }

    private static void validateModel(Method method, Object model) {
        for (var param : method.getParameters()) {
            if (param.isAnnotationPresent(Model.class) && !param.getType().isInstance(model)) {
                throw new IllegalArgumentException("Missing or incompatible model for " + method);
            }
        }
    }

    private static Definition index(MenuHandler handler) {
        Map<String, Method> screens = new HashMap<>();
        Map<String, Method> actions = new HashMap<>();
        for (Class<?> type = handler.getClass(); type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if ((method.isAnnotationPresent(Screen.class) || method.isAnnotationPresent(Action.class))
                        && !Modifier.isPublic(method.getModifiers())) {
                    throw new IllegalArgumentException("Menu methods must be public: " + method);
                }
            }
        }
        for (Method method : handler.getClass().getMethods()) {
            Screen screen = method.getAnnotation(Screen.class);
            Action action = method.getAnnotation(Action.class);
            if (screen == null && action == null) continue;
            if (Modifier.isStatic(method.getModifiers()) || (screen != null && action != null)
                    || method.getReturnType() != (screen != null ? MenuView.class : void.class)) {
                throw new IllegalArgumentException("Invalid menu method: " + method);
            }
            for (var param : method.getParameters()) {
                if (param.isAnnotationPresent(Model.class) && !param.getType().isPrimitive()) continue;
                Class<?> type = param.getType();
                if (type != Player.class && type != MenuFlow.class && !(action != null && type == MenuContext.class)) {
                    throw new IllegalArgumentException("Unsupported menu parameter: " + param);
                }
            }
            String name = screen != null ? screen.value() : action.value();
            Map<String, Method> target = screen != null ? screens : actions;
            if (name.isBlank() || target.putIfAbsent(name, method) != null) {
                throw new IllegalArgumentException("Duplicate or blank menu route: " + name);
            }
        }
        if (!screens.containsKey("main")) throw new IllegalArgumentException("Menu needs @Screen main: " + handler);
        return new Definition(handler, Map.copyOf(screens), Map.copyOf(actions));
    }

    private record Definition(MenuHandler handler, Map<String, Method> screens, Map<String, Method> actions) {}

    private static final class Session {
        private final MenuFlow flow;
        private final Inventory inventory;
        private final Map<Integer, MenuItem> items;
        private boolean pending;

        private Session(MenuFlow flow, Inventory inventory, Map<Integer, MenuItem> items) {
            this.flow = flow;
            this.inventory = inventory;
            this.items = items;
        }

        private MenuFlow flow() { return flow; }
        private Inventory inventory() { return inventory; }
        private Map<Integer, MenuItem> items() { return items; }
        private boolean pending() { return pending; }
        private void pending(boolean value) { pending = value; }
    }
}
