package io.paradaux.hibernia.framework.configurator.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigurationValue {
    /**
     * The path to the configuration value in the YAML file
     */
    String path();

    /**
     * Optional default value if the path doesn't exist
     */
    String defaultValue() default "";
}
