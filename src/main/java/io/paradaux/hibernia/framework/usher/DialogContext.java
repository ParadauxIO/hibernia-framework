package io.paradaux.hibernia.framework.usher;

import io.papermc.paper.dialog.DialogResponseView;
import net.kyori.adventure.audience.Audience;
import org.jetbrains.annotations.Nullable;

/**
 * The context handed to an {@link io.paradaux.hibernia.framework.usher.annotations.Action @Action}
 * invocation: the submitted {@link DialogResponseView}, the viewing {@link Audience}, and the owning
 * {@link DialogFlow}. An action method may declare a {@code DialogContext} parameter to read inputs
 * directly, though {@code @Input}-typed parameters are usually cleaner.
 *
 * @param view  the player's submitted input values
 * @param flow  the navigation flow this click belongs to
 */
public record DialogContext(DialogResponseView view, DialogFlow flow) {

    /** The audience that clicked (the viewer). */
    public Audience audience() {
        return flow.viewer();
    }

    /** Raw text/option-id value for {@code key}, or {@code null}. */
    public @Nullable String text(String key) {
        return view.getText(key);
    }

    /** Raw checkbox value for {@code key}, or {@code null}. */
    public @Nullable Boolean bool(String key) {
        return view.getBoolean(key);
    }

    /** Raw slider value for {@code key}, or {@code null}. */
    public @Nullable Float number(String key) {
        return view.getFloat(key);
    }
}
