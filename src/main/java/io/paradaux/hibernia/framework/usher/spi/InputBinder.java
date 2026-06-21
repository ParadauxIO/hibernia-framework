package io.paradaux.hibernia.framework.usher.spi;

import io.papermc.paper.dialog.DialogResponseView;

/**
 * Reads one dialog input back into a typed value — the dialog-tier analogue of
 * {@link io.paradaux.hibernia.framework.commander.spi.ParameterResolver}.
 *
 * <p>When an {@link io.paradaux.hibernia.framework.usher.annotations.Action @Action} method declares
 * a parameter {@code @Input("key") T value}, the framework looks up the {@code InputBinder} registered
 * against {@code T} (the boxed type for primitives) and calls {@link #read(DialogResponseView, String)}
 * with the player's submitted response. This removes the stringly-typed readback
 * ({@code view.getText("k").equals("enabled")}) that dialog code otherwise repeats at every call site.</p>
 *
 * <p>Built-ins cover {@code String}, {@code Boolean}, {@code Integer}, {@code Long}, {@code Float} and
 * {@code Double}; additional binders (e.g. for a domain enum or a service-resolved type) are bound via a
 * Guice {@code Multibinder<InputBinder<?>>}.</p>
 *
 * @param <T> the value type produced
 */
public interface InputBinder<T> {

    /** The value class this binder produces (use the boxed type for primitives). */
    Class<T> type();

    /**
     * Read the input identified by {@code key} from the submitted response.
     *
     * @return the typed value, or {@code null} when the input is absent or blank
     */
    T read(DialogResponseView view, String key);
}
