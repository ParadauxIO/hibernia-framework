package io.paradaux.hibernia.framework.usher.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method invoked when a dialog button bound to its name is clicked — the dialog-tier analogue
 * of a command-dispatch target.
 *
 * <p>A {@link io.paradaux.hibernia.framework.usher.DialogView} button references an action by name
 * ({@code .button("label.key", "submit")}); clicking it runs the {@code @Action("submit")} method.
 * Its parameters are injected: {@link Input @Input} inputs (typed via registered
 * {@link io.paradaux.hibernia.framework.usher.spi.InputBinder}s), the flow's {@link Model @Model}
 * object, the viewing {@code Player}/{@code Audience}, the
 * {@link io.paradaux.hibernia.framework.usher.DialogFlow}, and the
 * {@link io.paradaux.hibernia.framework.usher.DialogContext}.</p>
 *
 * <p>The method is invoked on the server main thread. Exceptions are caught and rendered to the viewer;
 * the framework's HTTP-semantic exceptions map to messages the same way command dispatch does.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Action {

    /** The action name a button references to invoke this method. */
    String value();
}
