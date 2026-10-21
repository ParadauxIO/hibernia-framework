package io.paradaux.hibernia.framework.curator;

import io.paradaux.hibernia.framework.curator.annotations.Action;
import io.paradaux.hibernia.framework.curator.annotations.Model;
import io.paradaux.hibernia.framework.curator.annotations.Screen;
import io.paradaux.hibernia.framework.curator.spi.MenuHandler;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuManagerTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Server server = mock(Server.class);
    private final PluginManager plugins = mock(PluginManager.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final Player player = mock(Player.class);
    private final InventoryView open = mock(InventoryView.class);
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final Handler handler = new Handler();
    private final State state = new State();
    private MenuManager manager;
    private Inventory top;

    @BeforeEach
    void setup() {
        when(plugin.getServer()).thenReturn(server);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(server.isPrimaryThread()).thenReturn(true);
        when(server.getPluginManager()).thenReturn(plugins);
        when(server.getScheduler()).thenReturn(scheduler);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getOpenInventory()).thenReturn(open);
        when(open.getTopInventory()).thenAnswer(call -> top);
        when(server.createInventory(isNull(), anyInt(), any(Component.class))).thenAnswer(call -> {
            Inventory inventory = mock(Inventory.class);
            when(inventory.getSize()).thenReturn(call.getArgument(1));
            return inventory;
        });
        when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
            Inventory previous = top;
            top = call.getArgument(0);
            if (previous != null) manager.onClose(closeEvent(previous, InventoryCloseEvent.Reason.OPEN_NEW));
            return open;
        });
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            tasks.add(call.getArgument(1));
            return null;
        });
        manager = new MenuManager(plugin, Set.of(handler), null);
        manager.registerAll();
    }

    private MenuFlow start() { return manager.open(player, Handler.class, state); }
    private void tick() { while (!tasks.isEmpty()) tasks.remove().run(); }

    private InventoryClickEvent click(int slot, ClickType type) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(open);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getClick()).thenReturn(type);
        manager.onClick(event);
        return event;
    }

    private InventoryCloseEvent closeEvent(Inventory inventory, InventoryCloseEvent.Reason reason) {
        InventoryCloseEvent event = mock(InventoryCloseEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getInventory()).thenReturn(inventory);
        when(event.getReason()).thenReturn(reason);
        return event;
    }

    @Test
    void registrationIsIdempotentAndOnlyTopLeftRightClicksDispatchNextTick() {
        manager.registerAll();
        verify(plugins).registerEvents(manager, plugin);
        var flow = start();
        for (ClickType type : List.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT)) {
            var event = click(4, type);
            verify(event).setCancelled(true);
            int before = state.clicks.size();
            tick();
            assertEquals(before + 1, state.clicks.size());
            assertEquals(type, state.clicks.getLast());
            assertEquals(4, state.selected);
            assertSame(player, state.player);
        }
        for (ClickType type : List.of(ClickType.NUMBER_KEY, ClickType.SWAP_OFFHAND, ClickType.DOUBLE_CLICK,
                ClickType.MIDDLE, ClickType.DROP, ClickType.CONTROL_DROP, ClickType.CREATIVE)) {
            verify(click(4, type)).setCancelled(true);
        }
        verify(click(-999, ClickType.LEFT)).setCancelled(true);
        verify(click(54, ClickType.SHIFT_LEFT)).setCancelled(true);
        verify(click(49, ClickType.LEFT)).setCancelled(true);
        tick();
        assertEquals(4, state.clicks.size());
        assertTrue(flow.active());
    }

    @Test
    void paginationAndPreviewReturnPreserveParentPage() {
        var flow = start();
        flow.previousPage();
        assertEquals(0, flow.page());
        flow.nextPage();
        assertEquals(1, flow.page());
        click(0, ClickType.RIGHT);
        tick();
        assertEquals(45, state.selected);
        flow.open("preview");
        assertEquals("preview", flow.current());
        assertEquals(0, flow.page());
        manager.onClose(closeEvent(top, InventoryCloseEvent.Reason.PLAYER));
        tick();
        assertEquals("main", flow.current());
        assertEquals(1, flow.page());
        flow.page(999);
        assertEquals(2, flow.page());
        state.count = 1;
        flow.refresh();
        assertEquals(0, flow.page());
        flow.back();
        assertFalse(flow.active());
        assertNull(flow.current());
        assertEquals(0, flow.page());
    }

    @Test
    void duplicateAndStaleClicksCannotAffectAnotherScreenOrFlow() {
        var flow = start();
        click(0, ClickType.LEFT);
        click(1, ClickType.LEFT);
        tick();
        assertEquals(List.of(ClickType.LEFT), state.clicks);
        click(1, ClickType.LEFT);
        flow.refresh();
        tick();
        assertEquals(1, state.clicks.size());
        click(2, ClickType.LEFT);
        var replacement = start();
        tick();
        assertEquals(1, state.clicks.size());
        assertFalse(flow.active());
        flow.close();
        assertTrue(replacement.active());
        assertThrows(IllegalStateException.class, flow::refresh);
        assertThrows(IllegalArgumentException.class, () -> replacement.open("missing"));
    }

    @Test
    void dragAndBottomTransfersAreBlockedButForeignInventoriesAreUntouched() {
        start();
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player);
        when(drag.getView()).thenReturn(open);
        manager.onDrag(drag);
        verify(drag).setCancelled(true);
        top = mock(Inventory.class);
        verify(click(0, ClickType.LEFT), never()).setCancelled(true);
        clearInvocations(drag);
        manager.onDrag(drag);
        verify(drag, never()).setCancelled(true);
        manager.onClose(closeEvent(top, InventoryCloseEvent.Reason.PLAYER));
        tick();
        assertTrue(state.clicks.isEmpty());
    }

    @Test
    void latestAsyncResultWinsAndLoadingDoesNotLoseParentPage() {
        var flow = start();
        flow.page(2);
        CompletableFuture<String> old = new CompletableFuture<>(), latest = new CompletableFuture<>();
        List<String> results = new ArrayList<>();
        flow.await(old, loading(), (value, f) -> results.add(value));
        flow.await(latest, loading(), (value, f) -> { results.add(value); f.open("preview"); });
        old.complete("stale");
        latest.complete("current");
        assertTrue(results.isEmpty());
        tick();
        assertEquals(List.of("current"), results);
        flow.back();
        assertEquals(2, flow.page());
        CompletableFuture<String> abandoned = new CompletableFuture<>();
        flow.await(abandoned, loading(), (value, f) -> fail("Closed flow resumed"));
        flow.close();
        abandoned.complete("late");
        tick();
        assertFalse(flow.active());
    }

    @Test
    void navigationEscapeAndDisconnectInvalidateAsyncWork() {
        var flow = start();
        var future = new CompletableFuture<String>();
        flow.await(future, loading(), (v, f) -> fail("Navigation resurrected work"));
        flow.open("preview");
        future.complete("old");
        tick();
        var previewFuture = new CompletableFuture<String>();
        flow.await(previewFuture, MenuView.chest(Component.empty(), 1).backOnClose(true).build(),
                (v, f) -> fail("Escape resurrected work"));
        manager.onClose(closeEvent(top, InventoryCloseEvent.Reason.PLAYER));
        previewFuture.complete("old");
        tick();
        assertEquals("main", flow.current());
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        manager.onQuit(quit);
        manager.onQuit(quit);
        assertFalse(flow.active());
    }

    @Test
    void externalOpenAndRootEscapeEndJourneyAndPreviewDoesNotStealNewInventory() {
        var flow = start();
        manager.onClose(closeEvent(top, InventoryCloseEvent.Reason.OPEN_NEW));
        assertFalse(flow.active());
        flow = start();
        manager.onClose(closeEvent(top, InventoryCloseEvent.Reason.PLAYER));
        assertFalse(flow.active());
        flow = start();
        flow.open("preview");
        manager.onClose(closeEvent(top, InventoryCloseEvent.Reason.PLAYER));
        InventoryOpenEvent external = mock(InventoryOpenEvent.class);
        when(external.getPlayer()).thenReturn(player);
        when(external.getInventory()).thenReturn(mock(Inventory.class));
        manager.onOpen(external);
        tick();
        assertFalse(flow.active());
    }

    @Test
    void asyncFailureAndHandlerFailureCloseSafely() {
        var flow = start();
        flow.await(CompletableFuture.failedFuture(new IllegalStateException("database password")), loading(), (v, f) -> {});
        tick();
        assertFalse(flow.active());
        verify(player).sendMessage(any(Component.class));
        flow = start();
        state.failClick = true;
        click(0, ClickType.LEFT);
        tick();
        assertFalse(flow.active());
        flow = start();
        state.failScreen = true;
        flow.refresh();
        assertFalse(flow.active());
        state.failScreen = false;
        flow = start();
        flow.await(CompletableFuture.completedFuture("done"), loading(), (v, f) -> { throw new IllegalArgumentException(); });
        tick();
        assertFalse(flow.active());
    }

    @Test
    void disabledPluginDropsCompletionAndShutdownClosesOnlyOwnedInventory() {
        var flow = start();
        var future = new CompletableFuture<String>();
        flow.await(future, loading(), (v, f) -> fail());
        when(plugin.isEnabled()).thenReturn(false);
        future.complete("done");
        assertTrue(tasks.isEmpty());
        PluginDisableEvent other = mock(PluginDisableEvent.class);
        manager.onDisable(other);
        assertTrue(flow.active());
        PluginDisableEvent own = mock(PluginDisableEvent.class);
        when(own.getPlugin()).thenReturn(plugin);
        manager.onDisable(own);
        assertFalse(flow.active());
        manager.shutdown();
        assertThrows(IllegalStateException.class, this::start);
    }

    @Test
    void invalidOpenModelsAndThreadAreRejectedBeforeReplacingExistingFlow() {
        var flow = start();
        assertSame(state, flow.model(State.class));
        assertThrows(IllegalArgumentException.class, () -> manager.open(player, Handler.class, null));
        assertThrows(IllegalArgumentException.class, () -> manager.open(player, Handler.class, "wrong"));
        assertThrows(IllegalArgumentException.class, () -> manager.open(player, NoMain.class, null));
        assertTrue(flow.active());
        when(server.isPrimaryThread()).thenReturn(false);
        assertThrows(IllegalStateException.class, this::start);
        assertThrows(IllegalStateException.class, flow::refresh);
    }

    @Test
    void unknownActionsNullViewsAndCancelledOpenFailClosed() {
        var flow = start();
        state.badAction = true;
        flow.refresh();
        assertFalse(flow.active());
        state.badAction = false;
        state.nullView = true;
        assertFalse(start().active());
        state.nullView = false;
        when(player.openInventory(any(Inventory.class))).thenReturn(null);
        assertFalse(start().active());
    }

    @Test
    void invalidHandlerDefinitionsFailAtRegistration() {
        for (MenuHandler invalid : List.of(new NoMain(), new BadReturn(), new BadParameter(), new StaticScreen(),
                new DuplicateScreen(), new BlankScreen(), new BothAnnotations(), new PrivateScreen(),
                new PrimitiveModel(), new BadActionReturn())) {
            var isolated = new MenuManager(plugin, Set.of(invalid), null);
            assertThrows(IllegalArgumentException.class, isolated::registerAll, invalid.getClass().getName());
        }
    }

    @Test
    void cancelledRefreshCannotLeaveAnUnprotectedOldInventoryOpen() {
        var flow = start();
        when(player.openInventory(any(Inventory.class))).thenReturn(null);
        flow.refresh();
        assertFalse(flow.active());
        verify(player).closeInventory();
    }

    @Test
    void failedReplacementClosesOriginalAndThrowingOpenClosesOwnedView() {
        var original = start();
        state.failScreen = true;
        var replacement = start();
        assertFalse(original.active());
        assertFalse(replacement.active());
        verify(player).closeInventory();
        state.failScreen = false;
        var flow = start();
        when(player.openInventory(any(Inventory.class))).thenThrow(new IllegalStateException("open failed"));
        flow.refresh();
        assertFalse(flow.active());
        verify(player, times(2)).closeInventory();
    }

    @Test
    void viewersKeepIndependentModelsPagesAndLifecycle() {
        var first = start();
        Player second = mock(Player.class);
        InventoryView secondView = mock(InventoryView.class);
        Inventory[] secondTop = new Inventory[1];
        when(second.getUniqueId()).thenReturn(UUID.randomUUID());
        when(second.getOpenInventory()).thenReturn(secondView);
        when(secondView.getTopInventory()).thenAnswer(c -> secondTop[0]);
        when(second.openInventory(any(Inventory.class))).thenAnswer(c -> {
            secondTop[0] = c.getArgument(0);
            return secondView;
        });
        State secondState = new State();
        var secondFlow = manager.open(second, Handler.class, secondState);
        first.nextPage();
        assertEquals(0, secondFlow.page());
        assertSame(secondState, secondFlow.model(State.class));
        InventoryOpenEvent ownedOpen = mock(InventoryOpenEvent.class);
        when(ownedOpen.getPlayer()).thenReturn(second);
        when(ownedOpen.getInventory()).thenReturn(secondTop[0]);
        manager.onOpen(ownedOpen);
        assertTrue(secondFlow.active());
        first.close();
        assertTrue(secondFlow.active());
        verify(second, never()).closeInventory();
        manager.shutdown();
        assertFalse(secondFlow.active());
        verify(second).closeInventory();
    }

    @Test
    void findExampleUsesOriginalLayoutCommandsPreviewCacheAndPaging() {
        var lookup = mock(FindResultsMenuExample.ItemLookup.class);
        var icons = new FindResultsMenuExample.Icons(MenuViewTest.icon(), MenuViewTest.icon(),
                MenuViewTest.icon(), MenuViewTest.icon(), Component.text("Loading"));
        var example = new FindResultsMenuExample(icons, lookup);
        manager = new MenuManager(plugin, Set.of(example), null);
        manager.registerAll();
        var rows = IntStream.range(0, 46).mapToObj(i -> new FindResultsMenuExample.Shop("item" + i,
                MenuViewTest.icon(), "compass transit " + i + " 64 10", i == 0 ? "compass 0 64 10" : "")).toList();
        var model = new FindResultsMenuExample.State(Component.text("Find"), rows, "item1", MenuViewTest.icon());
        var flow = manager.open(player, FindResultsMenuExample.class, model);
        assertEquals(54, flow.view().size());
        assertNull(flow.view().items(0).get(48).action());
        click(50, ClickType.LEFT);
        tick();
        assertEquals(1, flow.page());
        assertNull(flow.view().items(1).get(50).action());
        var lookupFuture = new CompletableFuture<org.bukkit.inventory.ItemStack>();
        when(lookup.load("item45")).thenReturn(lookupFuture);
        click(0, ClickType.SHIFT_RIGHT);
        tick();
        lookupFuture.complete(MenuViewTest.icon());
        tick();
        assertEquals("preview", flow.current());
        assertEquals(27, flow.view().size());
        assertNotNull(flow.view().items(0).get(13));
        click(18, ClickType.LEFT);
        tick();
        assertEquals(1, flow.page());
        click(0, ClickType.RIGHT);
        tick();
        verify(lookup, times(1)).load("item45");
        manager.onClose(closeEvent(top, InventoryCloseEvent.Reason.PLAYER));
        tick();
        click(48, ClickType.LEFT);
        tick();
        assertEquals(0, flow.page());
        click(1, ClickType.RIGHT);
        tick();
        verify(lookup, never()).load("item1");
        flow.back();
        click(0, ClickType.RIGHT);
        tick();
        verify(player).performCommand("compass 0 64 10");
        assertFalse(flow.active());
        flow = manager.open(player, FindResultsMenuExample.class, model);
        click(2, ClickType.SHIFT_LEFT);
        tick();
        verify(player).performCommand("compass transit 2 64 10");
        assertFalse(flow.active());
    }

    private static MenuView loading() { return MenuView.chest(Component.text("Loading"), 1).build(); }

    public static class State {
        int count = 91;
        int selected;
        Player player;
        boolean failClick, failScreen, badAction, nullView;
        final List<ClickType> clicks = new ArrayList<>();
    }

    public static class Handler implements MenuHandler {
        @Screen
        public MenuView main(@Model State state, MenuFlow flow, Player player) {
            if (state.failScreen) throw new IllegalStateException("render");
            if (state.nullView) return null;
            return MenuView.chest(Component.text("Results"), 6)
                    .content(0, 44, IntStream.range(0, state.count)
                            .mapToObj(i -> MenuItem.button(MenuViewTest.icon(), state.badAction ? "unknown" : "select", i)).toList())
                    .slot(49, MenuItem.display(MenuViewTest.icon())).build();
        }

        @Screen("preview")
        public MenuView preview() { return MenuView.chest(Component.text("Preview"), 3).backOnClose(true).build(); }

        @Action("select")
        public void select(MenuContext context, @Model State state, Player player, MenuFlow flow) {
            if (state.failClick) throw new IllegalStateException("action");
            state.selected = context.payload(Integer.class);
            state.clicks.add(context.click());
            state.player = context.player();
            assertSame(flow, context.flow());
            assertTrue(context.slot() < 45);
        }
    }

    public static class NoMain implements MenuHandler {}
    public static class BadReturn implements MenuHandler { @Screen public void main() {} }
    public static class BadParameter implements MenuHandler { @Screen public MenuView main(String s) { return loading(); } }
    public static class StaticScreen implements MenuHandler { @Screen public static MenuView main() { return loading(); } }
    public static class DuplicateScreen extends Handler { @Screen public MenuView duplicate() { return loading(); } }
    public static class BlankScreen implements MenuHandler { @Screen(" ") public MenuView main() { return loading(); } }
    public static class BothAnnotations implements MenuHandler { @Screen @Action("x") public MenuView main() { return loading(); } }
    public static class PrivateScreen implements MenuHandler { @Screen private MenuView main() { return loading(); } }
    public static class PrimitiveModel implements MenuHandler { @Screen public MenuView main(@Model int i) { return loading(); } }
    public static class BadActionReturn extends Handler { @Action("x") public int x() { return 1; } }
}
