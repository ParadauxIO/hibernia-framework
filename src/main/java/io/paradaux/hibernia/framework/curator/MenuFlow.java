package io.paradaux.hibernia.framework.curator;

import io.paradaux.hibernia.framework.curator.spi.MenuHandler;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

/** One player's menu journey. All methods except completion of supplied futures require the server thread. */
public final class MenuFlow {
    private final MenuManager manager;
    private final Player player;
    private final Class<? extends MenuHandler> handler;
    private final Object model;
    private final Deque<Frame> stack = new ArrayDeque<>();
    private long revision;
    private boolean active = true;
    private MenuView view;

    MenuFlow(MenuManager manager, Player player, Class<? extends MenuHandler> handler, Object model) {
        this.manager = manager;
        this.player = player;
        this.handler = handler;
        this.model = model;
    }

    /** Pushes a screen, preserving the parent's page. */
    public void open(String screen) {
        requireActive();
        manager.requireScreen(handler, screen);
        stack.push(new Frame(screen, 0));
        refresh();
    }

    /** Returns to the previous screen and page, or closes at the root. */
    public void back() {
        requireActive();
        if (stack.size() <= 1) { close(); return; }
        stack.pop();
        refresh();
    }

    /** Rebuilds the current screen from the model. Invalidates pending clicks and async results. */
    public void refresh() {
        requireActive();
        revision++;
        manager.render(this);
    }

    /** Moves one page forward, stopping at the last page. */
    public void nextPage() { page(page() + 1); }

    /** Moves one page backward, stopping at the first page. */
    public void previousPage() { page(page() - 1); }

    /** Selects a zero-based page, clamped to the current results. */
    public void page(int page) {
        requireActive();
        stack.push(new Frame(stack.pop().screen(), Math.clamp(page, 0, view.pages() - 1)));
        refresh();
    }

    /** Ends the journey. Later async completions cannot reopen it. */
    public void close() {
        manager.close(this);
    }

    /**
     * Shows a transient loading view and delivers the latest result on the server thread.
     * Navigation, another request, close, disconnect or replacement discards stale results.
     * The caller starts the asynchronous work; this method does not run blocking work off-thread.
     */
    public <T> void await(CompletionStage<T> future, MenuView loading, BiConsumer<T, MenuFlow> completion) {
        requireActive();
        Objects.requireNonNull(future, "future");
        Objects.requireNonNull(completion, "completion");
        long request = ++revision;
        manager.show(this, Objects.requireNonNull(loading, "loading"), true);
        future.whenComplete((result, error) -> manager.later(() -> {
            if (!active || revision != request) return;
            if (error != null) manager.failure(this, error);
            else {
                try { completion.accept(result, this); }
                catch (Exception ex) { manager.failure(this, ex); }
            }
        }));
    }

    /** Viewing player. */
    public Player player() { return player; }

    /** Shared model, checked against its expected type. */
    public <T> T model(Class<T> type) { return type.cast(model); }

    /** Current screen name, or null after close. */
    public String current() { return stack.isEmpty() ? null : stack.peek().screen(); }

    /** Zero-based page of the current screen. */
    public int page() { return stack.isEmpty() ? 0 : stack.peek().page(); }

    /** Whether this journey can still accept input. */
    public boolean active() { return active; }

    Class<? extends MenuHandler> handler() { return handler; }
    Object model() { return model; }
    long revision() { return revision; }
    MenuView view() { return view; }

    void shown(MenuView view, boolean preservePage) {
        this.view = view;
        if (preservePage) return;
        Frame frame = stack.pop();
        stack.push(new Frame(frame.screen(), Math.min(frame.page(), view.pages() - 1)));
    }

    void cancelPending() { revision++; }

    void invalidate() {
        active = false;
        revision++;
        stack.clear();
    }

    private void requireActive() {
        manager.requireMain();
        if (!active) throw new IllegalStateException("Menu flow is closed");
    }

    private record Frame(String screen, int page) {}
}
