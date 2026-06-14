package io.paradaux.hibernia.framework.usher.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a dialog handler root — the dialog-tier analogue of
 * {@link io.paradaux.hibernia.framework.commander.annotations.Command @Command}.
 *
 * <p>The class must implement {@link io.paradaux.hibernia.framework.usher.spi.DialogHandler} and
 * declare at least one {@link Screen @Screen} method. The optional {@link #value()} is a namespace
 * used for log messages and for the custom-click keys the framework derives per button.</p>
 *
 * <p>Example:
 * <pre>
 * &#064;Dialog("find")
 * public final class FindDialog implements DialogHandler {
 *     &#064;Screen
 *     public DialogView main(&#064;Model FindState state) { ... }
 *
 *     &#064;Action("submit")
 *     public void submit(&#064;Model FindState state, &#064;Input("fuzzy") boolean fuzzy, DialogFlow flow) { ... }
 * }
 * </pre>
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Dialog {

    /**
     * Namespace for this handler, used in logs and to derive per-button keys. Defaults to an empty
     * string, in which case the simple class name (lower-cased) is used.
     *
     * @return the dialog namespace
     */
    String value() default "";
}
