package io.paradaux.hibernia.framework.commander.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a permission required to execute a command.
 *
 * <p>Can be applied at class level (applies to all routes in the class) or method level
 * (overrides class-level permission for that route).</p>
 *
 * <p>The CommandManager checks this permission before invoking the method and will reject
 * execution if the sender lacks it.</p>
 *
 * @return the permission node string
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Permission {
    String value();
}
