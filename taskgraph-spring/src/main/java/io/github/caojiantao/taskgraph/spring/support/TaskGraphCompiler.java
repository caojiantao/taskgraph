package io.github.caojiantao.taskgraph.spring.support;

import io.github.caojiantao.taskgraph.kernel.exception.GraphValidationException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskDefinition;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Spring DSL 到内核图定义的编译器。
 */
public final class TaskGraphCompiler {

    private final ConfigurableListableBeanFactory beanFactory;

    public TaskGraphCompiler(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public TaskGraph<?> compile(Class<?> graphInterface) {
        TaskGraphDefinition graphDefinition = graphInterface.getAnnotation(TaskGraphDefinition.class);
        if (graphDefinition == null) {
            throw new GraphValidationException("graph interface [" + graphInterface.getName() + "] missing @TaskGraphDefinition");
        }

        TaskGraph.TaskGraphBuilder<Object> graphBuilder = TaskGraph.<Object>builder()
                .graphId(graphDefinition.graphId())
                .timeoutMillis(resolveTimeout(graphDefinition.timeoutMillis()))
                .executor(resolveExecutor(graphDefinition.executor()));

        String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, graphInterface);
        if (beanNames.length == 0) {
            throw new GraphValidationException(
                    "graph [" + graphDefinition.graphId() + "] has no task implementation beans");
        }

        for (String beanName : beanNames) {
            validateBeanScope(beanName, graphDefinition.graphId());
            Object bean = beanFactory.getBean(beanName);
            // 任务注解和图归属都以目标类为准，避免被 AOP 代理类干扰。
            Class<?> targetClass = resolveTargetClass(bean);
            validateGraphOwnership(targetClass, graphInterface);
            TaskNodeDefinition nodeDefinition = targetClass.getAnnotation(TaskNodeDefinition.class);
            if (nodeDefinition == null) {
                throw new GraphValidationException(
                        "bean [" + beanName + "] in graph [" + graphDefinition.graphId() + "] missing @TaskNodeDefinition");
            }

            graphBuilder.addTask(buildTaskDefinition(beanName, bean, nodeDefinition));
        }

        return graphBuilder.build();
    }

    private void validateBeanScope(String beanName, String graphId) {
        BeanDefinition beanDefinition = beanFactory.getBeanDefinition(beanName);
        String scope = beanDefinition.getScope();
        if (StringUtils.hasText(scope) && !BeanDefinition.SCOPE_SINGLETON.equals(scope)) {
            throw new GraphValidationException(
                    "graph [" + graphId + "] bean [" + beanName + "] must be singleton");
        }
        if (beanDefinition.isLazyInit()) {
            throw new GraphValidationException(
                    "graph [" + graphId + "] bean [" + beanName + "] must not be lazy");
        }
    }

    private void validateGraphOwnership(Class<?> targetClass, Class<?> expectedGraphInterface) {
        Set<Class<?>> graphInterfaces = resolveGraphInterfaces(targetClass);
        if (graphInterfaces.size() != 1) {
            throw new GraphValidationException(
                    "task implementation [" + targetClass.getName() + "] must implement exactly one graph interface");
        }
        Class<?> actualGraphInterface = graphInterfaces.iterator().next();
        if (!expectedGraphInterface.equals(actualGraphInterface)) {
            throw new GraphValidationException(
                    "task implementation [" + targetClass.getName() + "] does not belong to graph interface ["
                            + expectedGraphInterface.getName() + "]");
        }
    }

    private Set<Class<?>> resolveGraphInterfaces(Class<?> targetClass) {
        LinkedHashSet<Class<?>> graphInterfaces = new LinkedHashSet<>();
        Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(targetClass);
        for (Class<?> candidate : interfaces) {
            if (candidate.isAnnotationPresent(TaskGraphDefinition.class)) {
                graphInterfaces.add(candidate);
            }
        }
        return graphInterfaces;
    }

    @SuppressWarnings("unchecked")
    private TaskDefinition<Object> buildTaskDefinition(String beanName,
                                                       Object bean,
                                                       TaskNodeDefinition nodeDefinition) {
        GraphTask<Object> graphTask = castGraphTask(beanName, bean);
        // 真正执行时仍然调用 Spring 容器里的 Bean 实例，这样事务、AOP 等增强都还能生效。
        TaskDefinition.TaskDefinitionBuilder<Object> builder = TaskDefinition.<Object>builder()
                .taskId(nodeDefinition.taskId())
                .executor(resolveExecutor(nodeDefinition.executor()))
                .timeoutMillis(resolveTimeout(nodeDefinition.timeoutMillis()))
                .handler(graphTask::handle)
                .errorHandler(graphTask::onError);

        for (String upstream : nodeDefinition.dependsOn()) {
            builder.dependsOn(upstream);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private GraphTask<Object> castGraphTask(String beanName, Object bean) {
        if (!(bean instanceof GraphTask)) {
            throw new GraphValidationException("bean [" + beanName + "] must implement GraphTask");
        }
        return (GraphTask<Object>) bean;
    }

    private ExecutorService resolveExecutor(String beanName) {
        if (!StringUtils.hasText(beanName)) {
            return null;
        }
        if (!beanFactory.containsBean(beanName)) {
            throw new GraphValidationException("executor bean [" + beanName + "] does not exist");
        }
        Object bean = beanFactory.getBean(beanName);
        if (!(bean instanceof ExecutorService)) {
            throw new GraphValidationException("executor bean [" + beanName + "] must implement ExecutorService");
        }
        return (ExecutorService) bean;
    }

    private Long resolveTimeout(long timeoutMillis) {
        return timeoutMillis < 0L ? null : Long.valueOf(timeoutMillis);
    }

    private Class<?> resolveTargetClass(Object bean) {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        return targetClass == null ? bean.getClass() : targetClass;
    }
}
