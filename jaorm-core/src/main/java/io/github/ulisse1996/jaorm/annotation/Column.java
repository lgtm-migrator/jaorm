package io.github.ulisse1996.jaorm.annotation;

import java.lang.annotation.*;

/**
 * Specifies a Table Column that must be used for select/update current Entity
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
@Documented
public @interface Column {

    /**
     * The Column Name
     */
    String name();
}