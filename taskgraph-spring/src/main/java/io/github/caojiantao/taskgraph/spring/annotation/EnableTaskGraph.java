package io.github.caojiantao.taskgraph.spring.annotation;

import io.github.caojiantao.taskgraph.spring.config.TaskGraphImportRegistrar;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用 TaskGraph 纯 Spring 接入能力。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(TaskGraphImportRegistrar.class)
public @interface EnableTaskGraph {

    /**
     * 可选扫描包路径；为空时默认扫描当前配置类所在包及其子包。
     *
     * @return 包路径数组
     */
    String[] basePackages() default {};
}
