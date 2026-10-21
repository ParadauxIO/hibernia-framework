package io.paradaux.hibernia.framework.curator;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** A validated top-inventory click, delivered on the next server tick after cancelling item movement. */
public record MenuContext(MenuFlow flow, int slot, ClickType click, Object value) {
    /** The player who clicked. */
    public Player player() { return flow.player(); }

    /** The selected entry's payload, checked against the requested type. */
    public <T> T payload(Class<T> type) { return type.cast(value); }
}
