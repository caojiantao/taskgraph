package io.github.caojiantao.taskgraph.spring.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 任务节点定义注解，标注在任务实现类上。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TaskNodeDefinition {

    /**
     * 任务标识。
     *
     * @return 任务标识
     */
    String taskId();

    /**
     * 上游依赖任务标识列表。
     *
     * @return 依赖任务标识
     */
    String[] dependsOn() default {};

    /**
     * 任务级线程池 Bean 名；留空表示不显式指定。
     *
     * @return 线程池 Bean 名
     */
    String executor() default "";

    /**
     * 任务级超时时间，单位毫秒；小于 0 表示未配置。
     *
     * @return 超时时间
     */
    long timeoutMillis() default -1L;
}
