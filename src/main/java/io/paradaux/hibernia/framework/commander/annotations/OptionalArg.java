package io.paradaux.hibernia.framework.commander.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method parameter as an optional command argument with a default value.
 *
 * <p>If the argument is omitted by the caller, the {@code defaultValue} will be used.
 * The CommandManager will attempt to resolve the string default to the target parameter
 * type using registered {@code ParameterResolver}s.</p>
 *
 * <p>Example:
 * <pre>
 * @Route("balance [player]")
 * public void balance(@OptionalArg(value = "player", defaultValue = "self") OfflinePlayer player) { ... }
 * </pre>
 * </p>
 *
 * @see io.paradaux.hibernia.framework.commander.CommandManager
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface OptionalArg {
    /**
     * The name of the argument as used in the route placeholder.
     *
     * @return the argument name
     */
    String value(); // name

    /**
     * A string form of the default value to use when the argument is not provided.
     * The framework will attempt to resolve this value to the parameter type.
     *
     * @return the default value as string
     */
    String defaultValue() default "";
}
