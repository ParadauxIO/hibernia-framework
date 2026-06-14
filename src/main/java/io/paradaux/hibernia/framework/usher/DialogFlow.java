package io.paradaux.hibernia.framework.usher;

import io.paradaux.hibernia.framework.usher.spi.DialogHandler;
import net.kyori.adventure.audience.Audience;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * One player's journey through a dialog handler's screens: the viewer, the shared
 * {@link io.paradaux.hibernia.framework.usher.annotations.Model model}, and a navigation back-stack.
 *
 * <p>Injected into {@link io.paradaux.hibernia.framework.usher.annotations.Screen @Screen} and
 * {@link io.paradaux.hibernia.framework.usher.annotations.Action @Action} methods, it replaces the
 * by-hand {@code Supplier<Dialog> previous} threading that static dialog code relies on:</p>
 * <pre>
 * flow.open("filters");   // push the "filters" screen
 * flow.back();            // pop, re-show the previous screen
 * flow.refresh();         // re-render the current screen (e.g. after mutating the model)
 * flow.close();           // dismiss
 * flow.await(future, Text.key("find.querying"), (results, f) -> { ... }); // async with wait-screen
 * </pre>
 */
public final class DialogFlow {

    private final DialogManager manager;
    private final Player viewer;
    private final Class<? extends DialogHandler> handlerType;
    private final Object model;
    private final boolean bedrock;
    private final Deque<String> stack = new ArrayDeque<>();

    DialogFlow(DialogManager manager, Player viewer, Class<? extends DialogHandler> handlerType,
               @Nullable Object model, boolean bedrock) {
        this.manager = manager;
        this.viewer = viewer;
        this.handlerType = handlerType;
        this.model = model;
        this.bedrock = bedrock;
    }

    /** Push {@code screen} onto the stack and show it. */
    public void open(String screen) {
        stack.push(screen);
        manager.renderScreen(this, screen);
    }

    /** Pop the current screen and re-show the one beneath; closes the dialog if this was the last. */
    public void back() {
        if (stack.size() <= 1) {
            close();
            return;
        }
        stack.pop();
        manager.renderScreen(this, stack.peek());
    }

    /** Re-render the current screen (e.g. after mutating the model). */
    public void refresh() {
        String current = stack.peek();
        if (current != null) {
            manager.renderScreen(this, current);
        }
    }

    /** Dismiss the dialog and clear the stack. */
    public void close() {
        stack.clear();
        manager.closeFor(this);
    }

    /**
     * Show a transient wait-screen, run {@code future} off the main thread, then deliver its result to
     * {@code onComplete} back on the main thread. Mirrors the show-wait → query → show-results pattern
     * dialog code otherwise writes by hand. On failure the viewer gets the framework's internal-error
     * message and the throwable is logged.
     *
     * @param future     the asynchronous work
     * @param waitText   the message shown while waiting
     * @param onComplete receives the result and this flow, on the main thread
     */
    public <T> void await(CompletableFuture<T> future, Text waitText, BiConsumer<T, DialogFlow> onComplete) {
        manager.showWait(this, waitText);
        future.whenComplete((result, error) -> manager.runMain(() -> {
            if (error != null) {
                manager.handleAsyncError(this, error);
                return;
            }
            onComplete.accept(result, this);
        }));
    }

    /** The viewing player. */
    public Player player() {
        return viewer;
    }

    /** The viewer as an {@link Audience}. */
    public Audience viewer() {
        return viewer;
    }

    /** The flow's model object, or {@code null} if opened without one. */
    public @Nullable Object model() {
        return model;
    }

    /** Whether the viewer is a Bedrock (Geyser/Floodgate) player, per the bound {@code BedrockSupport}. */
    public boolean isBedrockViewer() {
        return bedrock;
    }

    /** The current screen name, or {@code null} before the first {@link #open(String)}. */
    public @Nullable String current() {
        return stack.peek();
    }

    Class<? extends DialogHandler> handlerType() {
        return handlerType;
    }
}
