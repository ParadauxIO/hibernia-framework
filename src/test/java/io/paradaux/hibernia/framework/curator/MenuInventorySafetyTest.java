package io.paradaux.hibernia.framework.curator;

import io.paradaux.hibernia.framework.curator.annotations.Action;
import io.paradaux.hibernia.framework.curator.annotations.Screen;
import io.paradaux.hibernia.framework.curator.spi.MenuHandler;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Event integration contracts, not a reimplementation of Minecraft's item-transfer algorithm.
 * Real Bukkit event objects pass through registered listeners; MockBukkit supplies inventory state.
 * DENY is the server's contract to reject the transfer. Vanilla packet processing is not simulated here.
 */
@ExtendWith(MenuInventorySafetyTest.NoSkippedOperations.class)
class MenuInventorySafetyTest {
    private ServerMock server;
    private JavaPlugin plugin;
    private PlayerMock viewer;
    private MenuManager manager;
    private Handler handler;
    private MenuFlow flow;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = server.addPlayer();
        handler = new Handler();
        manager = new MenuManager(plugin, Set.of(handler), null);
        manager.registerAll();
        flow = manager.open(viewer, Handler.class, null);
        viewer.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 17));
        viewer.getInventory().setItem(8, new ItemStack(Material.GOLD_INGOT, 23));
        viewer.getInventory().setItemInOffHand(new ItemStack(Material.EMERALD, 11));
        viewer.setItemOnCursor(new ItemStack(Material.IRON_INGOT, 7));
    }

    @AfterEach
    void teardown() {
        try { if (manager != null) manager.shutdown(); }
        finally { MockBukkit.unmock(); }
    }

    @ParameterizedTest
    @EnumSource(InventoryAction.class)
    void everyTransferActionIsDeniedInTopBottomAndOutsideSlots(InventoryAction action) {
        InventoryView view = viewer.getOpenInventory();
        ItemStack[] playerBefore = snapshot(viewer.getInventory());
        ItemStack[] topBefore = snapshot(view.getTopInventory());
        ItemStack cursorBefore = viewer.getItemOnCursor().clone();
        // Actionable icon, decorative icon, empty top slot, bottom storage, hotbar and outside.
        for (int slot : new int[]{0, 1, 2, 9, 36, -999}) {
            for (ClickType click : ClickType.values()) {
                var event = new InventoryClickEvent(view, slot < 0 ? InventoryType.SlotType.OUTSIDE
                        : InventoryType.SlotType.CONTAINER, slot, click, action, 8);
                server.getPluginManager().callEvent(event);
                assertEquals(Event.Result.DENY, event.getResult(), action + "/" + click + "/" + slot);
                assertArrayEquals(playerBefore, viewer.getInventory().getContents(), "player inventory changed");
                assertArrayEquals(topBefore, view.getTopInventory().getContents(), "display changed");
                assertEquals(cursorBefore, viewer.getItemOnCursor(), "cursor changed");
            }
        }
        // Exercise listener registration and a queued callback too, without using the transfer action as an oracle.
        server.getScheduler().performOneTick();
        assertEquals(1, handler.clicks);
    }

    @Test
    void dragCannotDistributeCursorIntoTopBottomOrMixedSlots() {
        ItemStack[] before = snapshot(viewer.getInventory());
        ItemStack[] topBefore = snapshot(viewer.getOpenInventory().getTopInventory());
        for (boolean single : new boolean[]{false, true}) {
            for (Map<Integer, ItemStack> slots : java.util.List.of(
                    Map.of(2, new ItemStack(Material.IRON_INGOT)),
                    Map.of(9, new ItemStack(Material.IRON_INGOT)),
                    Map.of(2, new ItemStack(Material.IRON_INGOT), 9, new ItemStack(Material.IRON_INGOT)))) {
                var event = new InventoryDragEvent(viewer.getOpenInventory(), new ItemStack(Material.IRON_INGOT, 5),
                        viewer.getItemOnCursor().clone(), single, slots);
                server.getPluginManager().callEvent(event);
                assertEquals(Event.Result.DENY, event.getResult());
                assertArrayEquals(before, viewer.getInventory().getContents());
                assertArrayEquals(topBefore, viewer.getOpenInventory().getTopInventory().getContents());
                assertEquals(7, viewer.getItemOnCursor().getAmount());
            }
        }
        server.getScheduler().performOneTick();
        assertEquals(0, handler.clicks);
    }

    @Test
    void creativePacketsAreDeniedAndNeverInvokeActions() {
        viewer.setGameMode(GameMode.CREATIVE);
        for (int slot : new int[]{0, 1, 9, 36, -999}) {
            var event = new InventoryCreativeEvent(viewer.getOpenInventory(),
                    slot < 0 ? InventoryType.SlotType.OUTSIDE : InventoryType.SlotType.CONTAINER,
                    slot, new ItemStack(Material.DIAMOND_BLOCK, 64));
            server.getPluginManager().callEvent(event);
            assertEquals(Event.Result.DENY, event.getResult());
        }
        server.getScheduler().performOneTick();
        assertEquals(0, handler.clicks);
        assertFalse(viewer.getInventory().contains(Material.DIAMOND_BLOCK));
        assertEquals(Material.IRON_INGOT, viewer.getItemOnCursor().getType());
    }

    @Test
    void anotherPlayerCannotOpenOrExtractFromAnActiveDisplay() {
        Inventory display = viewer.getOpenInventory().getTopInventory();
        PlayerMock other = server.addPlayer();
        Inventory ordinary = server.createInventory(null, 9);
        other.openInventory(ordinary);
        assertSame(ordinary, other.getOpenInventory().getTopInventory());
        // Request the actual retained menu through Bukkit's normal open path.
        other.openInventory(display);
        assertNotSame(display, other.getOpenInventory().getTopInventory(), "foreign player acquired menu icons");
        assertTrue(flow.active());
        assertEquals(64, display.getItem(0).getAmount());
    }

    @Test
    void closedMenuReferenceCannotBeReopenedOrLooted() {
        Inventory display = viewer.getOpenInventory().getTopInventory();
        flow.close();
        assertTrue(display.isEmpty(), "retired display still contains mintable icons");
        viewer.openInventory(display);
        assertNotSame(display, viewer.getOpenInventory().getTopInventory());
        assertFalse(flow.active());
    }

    @Test
    void refreshRetiresOldInventoryAndStaleEventsStayDenied() {
        InventoryView oldView = viewer.getOpenInventory();
        Inventory old = oldView.getTopInventory();
        flow.refresh();
        assertTrue(old.isEmpty());
        var staleClick = new InventoryClickEvent(oldView, InventoryType.SlotType.CONTAINER,
                0, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(staleClick);
        assertEquals(Event.Result.DENY, staleClick.getResult());
        server.getScheduler().performOneTick();
        assertEquals(0, handler.clicks);
        assertTrue(flow.active());
    }

    @Test
    void cancellingARefreshClearsTheRetiredDisplayAndLeavesNoActiveFlow() {
        Inventory display = viewer.getOpenInventory().getTopInventory();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler public void cancel(InventoryOpenEvent event) { event.setCancelled(true); }
        }, plugin);
        flow.refresh();
        assertFalse(flow.active());
        assertTrue(display.isEmpty());
        assertNotSame(display, viewer.getOpenInventory().getTopInventory());
    }

    @Test
    void shutdownClearsDisplayBeforeUnregisteringListenersAndPreservesPlayerItems() {
        Inventory display = viewer.getOpenInventory().getTopInventory();
        // Empty the cursor so closing does not legitimately return a cursor stack to the player's storage.
        viewer.setItemOnCursor(null);
        ItemStack[] before = snapshot(viewer.getInventory());
        manager.shutdown();
        assertTrue(display.isEmpty());
        assertArrayEquals(before, viewer.getInventory().getContents());
        assertFalse(flow.active());
        assertNotSame(display, viewer.getOpenInventory().getTopInventory());
    }

    @Test
    void completionAfterCloseDoesNotMintAnotherDisplay() {
        var future = new CompletableFuture<String>();
        flow.await(future, MenuView.chest(Component.text("Wait"), 1).build(), (value, current) -> current.refresh());
        Inventory display = viewer.getOpenInventory().getTopInventory();
        flow.close();
        future.complete("late");
        server.getScheduler().performOneTick();
        assertFalse(flow.active());
        assertNotSame(display, viewer.getOpenInventory().getTopInventory());
        assertFalse(viewer.getInventory().containsAtLeast(new ItemStack(Material.DIAMOND), 18));
    }

    @Test
    void itemSnapshotsDoNotAliasTemplatesOrRenderedCopies() {
        ItemStack template = new ItemStack(Material.DIAMOND, 64);
        template.editMeta(meta -> meta.displayName(Component.text("Display only")));
        MenuItem item = MenuItem.display(template);
        template.setAmount(1);
        template.editMeta(meta -> meta.displayName(Component.text("Changed")));
        ItemStack first = item.icon();
        first.setAmount(2);
        assertEquals(64, item.icon().getAmount());
        assertEquals(Component.text("Display only"), item.icon().getItemMeta().displayName());
    }

    @ParameterizedTest
    @EnumSource(InventoryCloseEvent.Reason.class)
    void everyCloseReasonRetiresIcons(InventoryCloseEvent.Reason reason) {
        Inventory display = viewer.getOpenInventory().getTopInventory();
        viewer.closeInventory(reason);
        server.getScheduler().performOneTick();
        assertFalse(flow.active(), reason.name());
        assertTrue(display.isEmpty(), reason.name());
        assertNotSame(display, viewer.getOpenInventory().getTopInventory());
    }

    @Test
    void disconnectRetiresIconsAndPendingClick() {
        Inventory display = viewer.getOpenInventory().getTopInventory();
        server.getPluginManager().callEvent(new InventoryClickEvent(viewer.getOpenInventory(),
                InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL));
        viewer.disconnect();
        server.getScheduler().performOneTick();
        assertTrue(display.isEmpty());
        assertFalse(flow.active());
        assertEquals(0, handler.clicks);
    }

    @Test
    void earlierListenerClosingDuringDragDoesNotExposeTheOldContents() {
        InventoryView old = viewer.getOpenInventory();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOW)
            public void close(InventoryDragEvent event) { flow.close(); }
        }, plugin);
        var event = new InventoryDragEvent(old, new ItemStack(Material.IRON_INGOT, 6),
                new ItemStack(Material.IRON_INGOT, 7), true, Map.of(2, new ItemStack(Material.IRON_INGOT)));
        server.getPluginManager().callEvent(event);
        assertEquals(Event.Result.DENY, event.getResult());
        assertTrue(old.getTopInventory().isEmpty());
        assertFalse(flow.active());
    }

    @Test
    void earlierListenerReplacingDuringClickCannotTransferFromTheOldDisplay() {
        InventoryView old = viewer.getOpenInventory();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOW)
            public void replace(InventoryClickEvent event) { flow.refresh(); }
        }, plugin);
        var event = new InventoryClickEvent(old, InventoryType.SlotType.CONTAINER, 0,
                ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(event);
        assertEquals(Event.Result.DENY, event.getResult());
        assertTrue(old.getTopInventory().isEmpty());
        server.getScheduler().performOneTick();
        assertEquals(0, handler.clicks);
        assertEquals(64, viewer.getOpenInventory().getTopInventory().getItem(0).getAmount());
    }

    @Test
    void retainedViewStaysProtectedForAnotherViewerEvenWhenOpenIsBypassed() {
        Inventory display = viewer.getOpenInventory().getTopInventory();
        PlayerMock other = server.addPlayer();
        // Construct a foreign view directly to exercise defence in depth, independently of the open guard.
        other.openInventory(server.createInventory(null, 9));
        var foreign = (org.mockbukkit.mockbukkit.inventory.InventoryViewMock) other.getOpenInventory();
        foreign.setTopInventory(display);
        var click = new InventoryClickEvent(foreign, InventoryType.SlotType.CONTAINER,
                0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        var drag = new InventoryDragEvent(foreign, new ItemStack(Material.IRON_INGOT, 6),
                new ItemStack(Material.IRON_INGOT, 7), true, Map.of(2, new ItemStack(Material.IRON_INGOT)));
        server.getPluginManager().callEvent(drag);
        assertEquals(Event.Result.DENY, click.getResult());
        assertEquals(Event.Result.DENY, drag.getResult());
        server.getScheduler().performOneTick();
        assertEquals(0, handler.clicks);
        assertTrue(other.getInventory().isEmpty());
        assertEquals(64, display.getItem(0).getAmount());
    }

    @Test
    void unrelatedInventoriesAndOtherManagersRemainIndependent() {
        JavaPlugin otherPlugin = MockBukkit.createMockPlugin("OtherMenus");
        Handler otherHandler = new Handler();
        MenuManager otherManager = new MenuManager(otherPlugin, Set.of(otherHandler), null);
        otherManager.registerAll();
        try {
            PlayerMock other = server.addPlayer();
            MenuFlow otherFlow = otherManager.open(other, Handler.class, null);
            Inventory otherDisplay = other.getOpenInventory().getTopInventory();
            assertSame(otherDisplay, otherDisplay.getHolder().getInventory());
            manager.shutdown();
            assertTrue(otherFlow.active());
            assertEquals(64, otherDisplay.getItem(0).getAmount());
            var click = new InventoryClickEvent(other.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                    0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            server.getPluginManager().callEvent(click);
            assertEquals(Event.Result.DENY, click.getResult());
            server.getScheduler().performOneTick();
            assertEquals(1, otherHandler.clicks);
            assertEquals(0, handler.clicks);
            Inventory ordinary = server.createInventory(null, 9);
            viewer.openInventory(ordinary);
            var ordinaryClick = new InventoryClickEvent(viewer.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                    0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            server.getPluginManager().callEvent(ordinaryClick);
            assertEquals(Event.Result.DEFAULT, ordinaryClick.getResult());
        } finally {
            otherManager.shutdown();
        }
    }

    private static ItemStack[] snapshot(Inventory inventory) {
        return Arrays.stream(inventory.getContents()).map(item -> item == null ? null : item.clone())
                .toArray(ItemStack[]::new);
    }

    /** MockBukkit marks unsupported operations as aborted tests; safety checks must fail instead. */
    public static final class NoSkippedOperations implements TestExecutionExceptionHandler {
        @Override
        public void handleTestExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
            if (failure instanceof org.opentest4j.TestAbortedException) {
                throw new AssertionError("Safety verification must not skip an unsupported server operation", failure);
            }
            throw failure;
        }
    }

    public static final class Handler implements MenuHandler {
        int clicks;
        @Screen public MenuView main() {
            return MenuView.chest(Component.text("Safety test"), 1)
                    .slot(0, MenuItem.button(new ItemStack(Material.DIAMOND, 64), "click", null))
                    .slot(1, MenuItem.display(new ItemStack(Material.EMERALD, 32))).build();
        }
        @Action("click") public void click() { clicks++; }
    }
}
