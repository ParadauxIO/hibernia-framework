package io.paradaux.hibernia.framework.usher.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that builds a screen of a dialog flow — the dialog-tier analogue of
 * {@link io.paradaux.hibernia.framework.commander.annotations.Route @Route}.
 *
 * <p>The method must return a {@link io.paradaux.hibernia.framework.usher.DialogView}. Its parameters
 * are injected by the framework: the flow's {@link Model @Model} object, the viewing
 * {@code Player}/{@code Audience}, and the {@link io.paradaux.hibernia.framework.usher.DialogFlow}.</p>
 *
 * <p>A handler may declare several {@code @Screen} methods; navigate between them with
 * {@link io.paradaux.hibernia.framework.usher.DialogFlow#open(String)}. The screen named {@code "main"}
 * (or the sole screen) is the default shown when a flow is opened.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Screen {

    /**
     * The screen name used for navigation. Defaults to the empty string, in which case the method
     * name is used.
     *
     * @return the screen name
     */
    String value() default "";
}
