package io.github.caojiantao.taskgraph.spring.runtime;

import io.github.caojiantao.taskgraph.kernel.exception.GraphValidationException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraphRegistry;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;
import io.github.caojiantao.taskgraph.spring.config.TaskGraphScanProperties;
import io.github.caojiantao.taskgraph.spring.support.TaskGraphCompiler;
import io.github.caojiantao.taskgraph.spring.support.TaskGraphScanner;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.ResolvableType;

/**
 * 启动期图注册器。
 */
public final class TaskGraphRegistrar implements SmartInitializingSingleton {

    private final TaskGraphScanProperties scanProperties;
    private final TaskGraphScanner scanner;
    private final TaskGraphCompiler compiler;
    private final TaskGraphRegistry registry;
    private final ContextGraphRouter router;

    public TaskGraphRegistrar(TaskGraphScanProperties scanProperties,
                              TaskGraphScanner scanner,
                              TaskGraphCompiler compiler,
                              TaskGraphRegistry registry,
                              ContextGraphRouter router) {
        this.scanProperties = scanProperties;
        this.scanner = scanner;
        this.compiler = compiler;
        this.registry = registry;
        this.router = router;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // 等单例 Bean 都就绪后再编译图，能保证拿到的是最终可用的任务 Bean。
        for (Class<?> graphInterface : scanner.scan(scanProperties)) {
            Class<?> contextType = resolveContextType(graphInterface);
            TaskGraph<?> taskGraph = compiler.compile(graphInterface);
            registry.register(taskGraph);
            router.register(contextType, taskGraph.getGraphId());
        }
    }

    private Class<?> resolveContextType(Class<?> graphInterface) {
        Class<?> contextType = ResolvableType.forClass(graphInterface).as(GraphTask.class).getGeneric(0).resolve();
        if (contextType == null) {
            throw new GraphValidationException(
                    "graph interface [" + graphInterface.getName() + "] must declare explicit context type");
        }
        return contextType;
    }
}
