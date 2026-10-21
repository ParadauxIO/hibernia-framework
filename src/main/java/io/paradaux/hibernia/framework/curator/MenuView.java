package io.paradaux.hibernia.framework.curator;

import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable chest layout with fixed slots and an optional paginated content area. Slots are zero-based. */
public final class MenuView {
    private final Component title;
    private final int rows;
    private final Map<Integer, MenuItem> fixed;
    private final List<Integer> contentSlots;
    private final List<MenuItem> content;
    private final boolean backOnClose;

    private MenuView(Builder builder) {
        title = builder.title;
        rows = builder.rows;
        fixed = Map.copyOf(builder.fixed);
        contentSlots = List.copyOf(builder.contentSlots);
        content = List.copyOf(builder.content);
        backOnClose = builder.backOnClose;
    }

    /** Starts a chest layout of one through six rows. Resolve localized titles with Message.component. */
    public static Builder chest(Component title, int rows) {
        return new Builder(title, rows);
    }

    /** The Adventure title. */
    public Component title() { return title; }

    /** Inventory size in slots. */
    public int size() { return rows * 9; }

    /** Whether escape from this screen returns to its parent. */
    public boolean backOnClose() { return backOnClose; }

    /** Number of pages, including one empty page for no results. */
    public int pages() {
        return content.isEmpty() ? 1 : 1 + (content.size() - 1) / contentSlots.size();
    }

    /** Builds a page, clamping an old page index after results shrink. Fixed controls remain on every page. */
    public Map<Integer, MenuItem> items(int page) {
        int start = Math.clamp(page, 0, pages() - 1) * contentSlots.size();
        Map<Integer, MenuItem> result = new LinkedHashMap<>(fixed);
        for (int i = 0; i < contentSlots.size() && start + i < content.size(); i++) {
            result.put(contentSlots.get(i), content.get(start + i));
        }
        return Map.copyOf(result);
    }

    /** Fluent layout builder. Duplicate and overlapping slots are rejected. */
    public static final class Builder {
        private final Component title;
        private final int rows;
        private final Map<Integer, MenuItem> fixed = new LinkedHashMap<>();
        private final List<Integer> contentSlots = new ArrayList<>();
        private final List<MenuItem> content = new ArrayList<>();
        private boolean backOnClose;

        private Builder(Component title, int rows) {
            this.title = Objects.requireNonNull(title, "title");
            if (rows < 1 || rows > 6) throw new IllegalArgumentException("Chest rows must be 1..6");
            this.rows = rows;
        }

        /** Places a fixed icon or button. */
        public Builder slot(int slot, MenuItem item) {
            checkSlot(slot);
            if (fixed.containsKey(slot) || contentSlots.contains(slot)) {
                throw new IllegalArgumentException("Slot already occupied: " + slot);
            }
            fixed.put(slot, Objects.requireNonNull(item, "item"));
            return this;
        }

        /** Reserves an inclusive slot range for paginated entries, in list order. Call once per layout. */
        public Builder content(int first, int last, List<MenuItem> entries) {
            checkSlot(first);
            checkSlot(last);
            if (first > last || !contentSlots.isEmpty()) throw new IllegalArgumentException("Invalid content range");
            for (int i = first; i <= last; i++) {
                if (fixed.containsKey(i)) throw new IllegalArgumentException("Slot already occupied: " + i);
            }
            content.addAll(List.copyOf(entries));
            for (int i = first; i <= last; i++) contentSlots.add(i);
            return this;
        }

        /** Fills unreserved slots with a decorative item. Call after placing content and controls. */
        public Builder fill(MenuItem decoration) {
            Objects.requireNonNull(decoration, "decoration");
            for (int i = 0; i < rows * 9; i++) {
                if (!fixed.containsKey(i) && !contentSlots.contains(i)) fixed.put(i, decoration);
            }
            return this;
        }

        /** Returns to the parent on player escape; opening another inventory never triggers this. */
        public Builder backOnClose(boolean value) {
            backOnClose = value;
            return this;
        }

        /** Takes a snapshot of this layout. */
        public MenuView build() { return new MenuView(this); }

        private void checkSlot(int slot) {
            if (slot < 0 || slot >= rows * 9) throw new IllegalArgumentException("Slot outside chest: " + slot);
        }
    }
}
