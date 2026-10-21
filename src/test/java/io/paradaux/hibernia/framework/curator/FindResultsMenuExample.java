package io.paradaux.hibernia.framework.curator;

import com.google.inject.Inject;
import io.paradaux.hibernia.framework.curator.annotations.Action;
import io.paradaux.hibernia.framework.curator.annotations.Model;
import io.paradaux.hibernia.framework.curator.annotations.Screen;
import io.paradaux.hibernia.framework.curator.spi.MenuHandler;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Compiled consumer example for ChestShopDatabase's results and preview screens.
 * Copy into the consuming plugin and bind Icons and ItemLookup using its config/i18n and database services.
 */
public final class FindResultsMenuExample implements MenuHandler {
    private final Icons icons;
    private final ItemLookup lookup;

    @Inject
    public FindResultsMenuExample(Icons icons, ItemLookup lookup) {
        this.icons = icons;
        this.lookup = lookup;
    }

    @Screen
    public MenuView results(@Model State state, MenuFlow flow) {
        var view = MenuView.chest(state.title, 6)
                .content(0, 44, state.shops.stream().map(shop -> MenuItem.button(shop.icon(), "shop", shop)).toList())
                .slot(45, MenuItem.button(icons.back(), "back", null))
                .slot(49, MenuItem.display(state.queriedItem));
        if (flow.page() > 0) view.slot(48, MenuItem.button(icons.previous(), "previous", null));
        if ((long) (flow.page() + 1) * 45 < state.shops.size()) {
            view.slot(50, MenuItem.button(icons.next(), "next", null));
        }
        return view.fill(MenuItem.display(icons.filler())).build();
    }

    @Screen("preview")
    public MenuView preview(@Model State state) {
        return MenuView.chest(Component.text("Item Preview: " + state.previewCode), 3)
                .slot(13, MenuItem.display(state.preview))
                .slot(18, MenuItem.button(icons.back(), "back", null))
                .fill(MenuItem.display(icons.filler()))
                .backOnClose(true).build();
    }

    @Action("shop")
    public void shop(MenuContext click, @Model State state, MenuFlow flow) {
        Shop shop = click.payload(Shop.class);
        String command = click.click().isRightClick() ? shop.rightCommand() : shop.leftCommand();
        if (command != null && !command.isBlank()) {
            flow.close();
            click.player().performCommand(command);
            return;
        }
        if (!click.click().isRightClick()) return;
        ItemStack cached = state.cache.get(shop.itemCode());
        CompletionStage<ItemStack> future = cached == null
                ? lookup.load(shop.itemCode()) : CompletableFuture.completedFuture(cached);
        flow.await(future, MenuView.chest(icons.loadingTitle(), 1)
                .fill(MenuItem.display(icons.filler())).build(), (item, current) -> {
            if (item == null) { current.refresh(); return; }
            state.cache.put(shop.itemCode(), item.clone());
            state.preview = item.clone();
            state.previewCode = shop.itemCode();
            current.open("preview");
        });
    }

    @Action("next")
    public void next(MenuFlow flow) { flow.nextPage(); }

    @Action("previous")
    public void previous(MenuFlow flow) { flow.previousPage(); }

    @Action("back")
    public void back(MenuFlow flow) { flow.back(); }

    /** The consumer renders icon templates and resolves command coordinates before building rows. */
    public record Shop(String itemCode, ItemStack icon, String leftCommand, String rightCommand) {}

    /** Resolve labels through Message and supply gray glass, arrow and paper icons from plugin config. */
    public record Icons(ItemStack filler, ItemStack back, ItemStack previous, ItemStack next, Component loadingTitle) {}

    /** Perform database work off-thread; deserialize server-dependent item data on the server thread. */
    @FunctionalInterface
    public interface ItemLookup {
        CompletionStage<ItemStack> load(String itemCode);
    }

    /** A fresh state per invocation; cached previews are scoped to this viewer's search. */
    public static final class State {
        private final Component title;
        private final List<Shop> shops;
        private final ItemStack queriedItem;
        private final Map<String, ItemStack> cache = new HashMap<>();
        private ItemStack preview;
        private String previewCode;

        public State(Component title, List<Shop> shops, String queriedCode, ItemStack queriedItem) {
            this.title = title;
            this.shops = List.copyOf(shops);
            this.queriedItem = queriedItem.clone();
            cache.put(queriedCode, queriedItem.clone());
        }
    }
}
