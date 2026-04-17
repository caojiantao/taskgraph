package io.github.caojiantao.taskgraph.spring.config;

import io.github.caojiantao.taskgraph.kernel.execution.DefaultGraphExecutor;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraphRegistry;
import io.github.caojiantao.taskgraph.spring.annotation.EnableTaskGraph;
import io.github.caojiantao.taskgraph.spring.runtime.ContextGraphRouter;
import io.github.caojiantao.taskgraph.spring.runtime.TaskGraphRegistrar;
import io.github.caojiantao.taskgraph.spring.runtime.TaskGraphTemplate;
import io.github.caojiantao.taskgraph.spring.support.TaskGraphCompiler;
import io.github.caojiantao.taskgraph.spring.support.TaskGraphScanner;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基础设施导入注册器。
 */
public final class TaskGraphImportRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableTaskGraph.class.getName()));
        List<String> basePackages = resolveBasePackages(importingClassMetadata, attributes);

        // 这里只注册框架自身基础设施，不在这一阶段触碰用户图定义。
        registerIfAbsent(registry, TaskGraphBeanNames.TASK_GRAPH_SCAN_PROPERTIES,
                BeanDefinitionBuilder.rootBeanDefinition(TaskGraphScanProperties.class)
                        .addConstructorArgValue(basePackages));
        registerIfAbsent(registry, TaskGraphBeanNames.TASK_GRAPH_SCANNER,
                BeanDefinitionBuilder.rootBeanDefinition(TaskGraphScanner.class));
        registerIfAbsent(registry, TaskGraphBeanNames.TASK_GRAPH_COMPILER,
                BeanDefinitionBuilder.rootBeanDefinition(TaskGraphCompiler.class));
        registerIfAbsent(registry, TaskGraphBeanNames.TASK_GRAPH_REGISTRY,
                BeanDefinitionBuilder.rootBeanDefinition(TaskGraphRegistry.class));
        registerIfAbsent(registry, TaskGraphBeanNames.GRAPH_EXECUTOR,
                BeanDefinitionBuilder.rootBeanDefinition(DefaultGraphExecutor.class));
        registerIfAbsent(registry, TaskGraphBeanNames.CONTEXT_GRAPH_ROUTER,
                BeanDefinitionBuilder.rootBeanDefinition(ContextGraphRouter.class));
        registerIfAbsent(registry, TaskGraphBeanNames.TASK_GRAPH_REGISTRAR,
                BeanDefinitionBuilder.rootBeanDefinition(TaskGraphRegistrar.class)
                        .addConstructorArgReference(TaskGraphBeanNames.TASK_GRAPH_SCAN_PROPERTIES)
                        .addConstructorArgReference(TaskGraphBeanNames.TASK_GRAPH_SCANNER)
                        .addConstructorArgReference(TaskGraphBeanNames.TASK_GRAPH_COMPILER)
                        .addConstructorArgReference(TaskGraphBeanNames.TASK_GRAPH_REGISTRY)
                        .addConstructorArgReference(TaskGraphBeanNames.CONTEXT_GRAPH_ROUTER));
        registerIfAbsent(registry, TaskGraphBeanNames.TASK_GRAPH_TEMPLATE,
                BeanDefinitionBuilder.rootBeanDefinition(TaskGraphTemplate.class)
                        .addConstructorArgReference(TaskGraphBeanNames.TASK_GRAPH_REGISTRY)
                        .addConstructorArgReference(TaskGraphBeanNames.CONTEXT_GRAPH_ROUTER)
                        .addConstructorArgReference(TaskGraphBeanNames.GRAPH_EXECUTOR));
    }

    private void registerIfAbsent(BeanDefinitionRegistry registry,
                                  String beanName,
                                  BeanDefinitionBuilder beanDefinitionBuilder) {
        if (!registry.containsBeanDefinition(beanName)) {
            registry.registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());
        }
    }

    private List<String> resolveBasePackages(AnnotationMetadata importingClassMetadata,
                                             AnnotationAttributes attributes) {
        String[] configuredPackages = attributes == null ? null : attributes.getStringArray("basePackages");
        if (configuredPackages != null && configuredPackages.length > 0) {
            ArrayList<String> basePackages = new ArrayList<>();
            for (String configuredPackage : configuredPackages) {
                if (StringUtils.hasText(configuredPackage)) {
                    basePackages.add(configuredPackage);
                }
            }
            if (!basePackages.isEmpty()) {
                return basePackages;
            }
        }

        // 未显式指定扫描包时，回退到启用配置类所在包，和常见 Spring 习惯保持一致。
        String defaultBasePackage = ClassUtils.getPackageName(importingClassMetadata.getClassName());
        if (!StringUtils.hasText(defaultBasePackage)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(defaultBasePackage);
    }
}
