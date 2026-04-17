package io.github.caojiantao.taskgraph.spring.support;

import io.github.caojiantao.taskgraph.kernel.exception.GraphValidationException;
import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.config.TaskGraphScanProperties;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 图接口扫描器。
 */
public final class TaskGraphScanner {

    public Set<Class<?>> scan(TaskGraphScanProperties scanProperties) {
        LinkedHashSet<Class<?>> graphInterfaces = new LinkedHashSet<>();
        InterfaceAwareScanner scanner = new InterfaceAwareScanner();
        for (String basePackage : scanProperties.getBasePackages()) {
            for (org.springframework.beans.factory.config.BeanDefinition beanDefinition
                    : scanner.findCandidateComponents(basePackage)) {
                graphInterfaces.add(resolveGraphInterface(beanDefinition.getBeanClassName()));
            }
        }
        return graphInterfaces;
    }

    private Class<?> resolveGraphInterface(String className) {
        try {
            Class<?> graphInterface = ClassUtils.forName(className, TaskGraphScanner.class.getClassLoader());
            if (!graphInterface.isInterface()) {
                throw new GraphValidationException(
                        "task graph definition [" + className + "] must be declared on an interface");
            }
            return graphInterface;
        } catch (ClassNotFoundException ex) {
            throw new GraphValidationException("failed to load task graph interface [" + className + "]", ex);
        }
    }

    /**
     * 允许把带图注解的接口识别为候选组件。
     */
    private static final class InterfaceAwareScanner extends ClassPathScanningCandidateComponentProvider {

        private InterfaceAwareScanner() {
            super(false);
            addIncludeFilter(new AnnotationTypeFilter(TaskGraphDefinition.class));
        }

        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
            return beanDefinition.getMetadata().isIndependent();
        }
    }
}
