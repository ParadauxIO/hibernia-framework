package io.paradaux.hibernia.framework.curator;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuViewTest {
    static ItemStack icon() {
        ItemStack icon = mock(ItemStack.class);
        when(icon.clone()).thenReturn(icon);
        return icon;
    }

    @Test
    void resultsUse45SlotsAndFixedFooterAcrossPageBoundaries() {
        var shops = IntStream.range(0, 91).mapToObj(i -> MenuItem.button(icon(), "shop", i)).toList();
        var preview = MenuItem.display(icon());
        var footer = MenuItem.display(icon());
        var view = MenuView.chest(Component.text("Results"), 6).content(0, 44, shops)
                .slot(49, preview).fill(footer).build();
        assertEquals(54, view.size());
        assertEquals(Component.text("Results"), view.title());
        assertEquals(3, view.pages());
        assertEquals(0, view.items(-1).get(0).payload());
        assertEquals(44, view.items(0).get(44).payload());
        assertEquals(45, view.items(1).get(0).payload());
        assertEquals(89, view.items(1).get(44).payload());
        assertEquals(90, view.items(99).get(0).payload());
        assertNull(view.items(2).get(1));
        assertSame(preview, view.items(2).get(49));
        assertSame(footer, view.items(2).get(45));
        assertThrows(UnsupportedOperationException.class, () -> view.items(0).clear());
    }

    @Test
    void emptyAndExactBoundaryPagesAndSnapshots() {
        var entries = new ArrayList<MenuItem>();
        var builder = MenuView.chest(Component.empty(), 1).content(0, 8, entries);
        entries.add(MenuItem.display(icon()));
        var empty = builder.build();
        assertEquals(1, empty.pages());
        assertTrue(empty.items(2).isEmpty());
        assertFalse(empty.backOnClose());
        var full = MenuView.chest(Component.empty(), 1)
                .content(0, 8, java.util.Collections.nCopies(9, entries.getFirst())).backOnClose(true).build();
        assertEquals(1, full.pages());
        assertEquals(9, full.items(0).size());
        assertTrue(full.backOnClose());
        assertEquals(1, MenuView.chest(Component.empty(), 1).build().pages());
    }

    @Test
    void rejectsAmbiguousOrOutOfBoundsLayouts() {
        var item = MenuItem.display(icon());
        assertThrows(IllegalArgumentException.class, () -> MenuView.chest(Component.empty(), 0));
        assertThrows(IllegalArgumentException.class, () -> MenuView.chest(Component.empty(), 7));
        assertThrows(IllegalArgumentException.class, () -> MenuView.chest(Component.empty(), 1).slot(-1, item));
        assertThrows(IllegalArgumentException.class, () -> MenuView.chest(Component.empty(), 1).slot(9, item));
        assertThrows(IllegalArgumentException.class, () -> MenuView.chest(Component.empty(), 1).slot(0, item).slot(0, item));
        assertThrows(IllegalArgumentException.class, () -> MenuView.chest(Component.empty(), 1)
                .content(0, 8, List.of()).slot(0, item));
        assertThrows(IllegalArgumentException.class, () -> MenuView.chest(Component.empty(), 1)
                .slot(4, item).content(0, 8, List.of()));
        assertThrows(IllegalArgumentException.class, () -> MenuView.chest(Component.empty(), 1).content(8, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> MenuView.chest(Component.empty(), 1)
                .content(0, 2, List.of()).content(3, 5, List.of()));
    }

    @Test
    void itemDefensivelyCopiesCustomStackAndRetainsPayload() {
        ItemStack original = mock(ItemStack.class), snapshot = mock(ItemStack.class), rendered = mock(ItemStack.class);
        when(original.clone()).thenReturn(snapshot);
        when(snapshot.clone()).thenReturn(rendered);
        var button = MenuItem.button(original, "preview", "DIAMOND");
        assertSame(rendered, button.icon());
        assertEquals("preview", button.action());
        assertEquals("DIAMOND", button.payload());
        assertNull(MenuItem.display(original).action());
        assertThrows(IllegalArgumentException.class, () -> MenuItem.button(original, " ", null));
        assertThrows(NullPointerException.class, () -> MenuItem.button(original, null, null));
    }
}
