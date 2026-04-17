package io.github.caojiantao.taskgraph.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 图级定义注解，标注在图接口上。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskGraphDefinition {

    /**
     * 图唯一标识。
     *
     * @return 图标识
     */
    String graphId();

    /**
     * 图级默认线程池 Bean 名；留空表示不显式指定。
     *
     * @return 线程池 Bean 名
     */
    String executor() default "";

    /**
     * 图总超时时间，单位毫秒；小于 0 表示未配置。
     *
     * @return 超时时间
     */
    long timeoutMillis() default -1L;
}
