package io.paradaux.hibernia.framework.commander.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a parameter as the command sender injection point.
 *
 * <p>The CommandManager will inject the command sender (for example {@code Player} or
 * {@code ConsoleCommandSender}) into the annotated parameter if the parameter type is compatible.</p>
 *
 * <p>Example:
 * <pre>
 * public void info(@Sender Player player) { ... }
 * </pre>
 * </p>
 *
 * @throws IllegalArgumentException if the runtime sender is not assignable to the parameter type
 * @see io.paradaux.hibernia.framework.commander.CommandManager#injectSender(Class, org.bukkit.command.CommandSender)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Sender {}
