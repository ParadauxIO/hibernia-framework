package io.paradaux.hibernia.framework.curator;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;

/** An immutable icon snapshot, optional action name, and action-specific payload. */
public record MenuItem(ItemStack icon, String action, Object payload) {
    /** Copies the supplied icon so subsequent mutations cannot change a rendered menu. */
    public MenuItem {
        icon = Objects.requireNonNull(icon, "icon").clone();
        if (action != null && action.isBlank()) throw new IllegalArgumentException("Blank action");
    }

    /** Returns a defensive copy, including custom item metadata and glint. */
    @Override
    public ItemStack icon() {
        return icon.clone();
    }

    /** A display-only item; it cannot be taken from the inventory. */
    public static MenuItem display(ItemStack icon) {
        return new MenuItem(icon, null, null);
    }

    /** An actionable icon with a typed payload available through {@link MenuContext#payload(Class)}. */
    public static MenuItem button(ItemStack icon, String action, Object payload) {
        return new MenuItem(icon, Objects.requireNonNull(action, "action"), payload);
    }
}
