package io.paradaux.hibernia.framework.usher.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the flow's model object into a {@link Screen @Screen} or {@link Action @Action} parameter.
 *
 * <p>A dialog flow carries one mutable model — the equivalent of ChestShop's {@code FindState} — shared
 * by every screen and action of the handler. The model is supplied when the flow is opened
 * ({@code dialogManager.open(player, FindDialog.class, findState)}) and threaded automatically
 * thereafter, replacing the by-hand parameter passing that static dialog code relies on.</p>
 *
 * <p>The parameter type must be assignable from the flow's model. A flow opened without a model leaves
 * {@code @Model} parameters {@code null}.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Model {
}
