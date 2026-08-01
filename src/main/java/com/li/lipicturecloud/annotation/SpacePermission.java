package com.li.lipicturecloud.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Checks a permission against one explicit space-related resource.
 * Resource expressions use Spring Expression Language and method arguments
 * are always available as #p0, #p1, ...
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SpacePermission {

    String value();

    String spaceId() default "";

    String pictureId() default "";

    String spaceUserId() default "";
}
