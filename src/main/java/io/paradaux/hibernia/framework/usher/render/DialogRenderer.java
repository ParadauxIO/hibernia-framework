package io.paradaux.hibernia.framework.usher.render;

import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.paradaux.hibernia.framework.usher.ButtonSpec;
import io.paradaux.hibernia.framework.usher.DialogView;
import io.paradaux.hibernia.framework.usher.Text;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import java.util.function.Function;

/**
 * Converts a renderer-agnostic {@link DialogView} into a concrete platform dialog and shows it to a
 * viewer. This is the single seam where the dialog tier touches Paper's dialog runtime — everything
 * above it ({@code DialogManager}, {@code DialogView}, binders, navigation) is plain data and is unit
 * tested with a fake renderer.
 *
 * @see PaperDialogRenderer
 */
public interface DialogRenderer {

    /**
     * Build and show {@code view} to {@code viewer}.
     *
     * @param viewer    the audience to show the dialog to
     * @param view      the screen spec
     * @param text      resolves a {@link Text} (message key or literal) to a {@link Component}
     * @param callbacks supplies the click callback for each button spec (already bound to the flow)
     */
    void show(Audience viewer,
              DialogView view,
              Function<Text, Component> text,
              Function<ButtonSpec, DialogActionCallback> callbacks);

    /** Close any dialog currently shown to {@code viewer}. */
    void close(Audience viewer);
}
