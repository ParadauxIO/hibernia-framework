package io.paradaux.hibernia.framework.usher.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds an {@link Action @Action} method parameter to a dialog input value — the dialog-tier analogue
 * of {@link io.paradaux.hibernia.framework.commander.annotations.Arg @Arg}.
 *
 * <p>The {@link #value()} is the input key declared on the screen
 * (e.g. {@code .toggle("fuzzy", "find.fuzzy")}). At click time the framework reads the submitted value
 * through the {@link io.paradaux.hibernia.framework.usher.spi.InputBinder} registered for the
 * parameter's type and passes it in — turning {@code view.getText("fuzzy").equals("enabled")} into a
 * plain {@code boolean fuzzy}.</p>
 *
 * <p>Example:
 * <pre>
 * &#064;Action("submit")
 * public void submit(&#064;Input("fuzzy") boolean fuzzy, &#064;Input("page") int page) { ... }
 * </pre>
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Input {

    /** The input key as declared on the screen's {@code DialogView}. */
    String value();
}
