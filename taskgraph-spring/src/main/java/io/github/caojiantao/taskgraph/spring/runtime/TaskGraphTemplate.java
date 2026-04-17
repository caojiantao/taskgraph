package io.github.caojiantao.taskgraph.spring.runtime;

import io.github.caojiantao.taskgraph.kernel.exception.GraphExecutionException;
import io.github.caojiantao.taskgraph.kernel.execution.GraphExecutionRequest;
import io.github.caojiantao.taskgraph.kernel.execution.GraphExecutor;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraphRegistry;
import io.github.caojiantao.taskgraph.kernel.result.GraphExecutionResult;

/**
 * Spring 侧统一执行门面。
 */
public final class TaskGraphTemplate {

    private final TaskGraphRegistry registry;
    private final ContextGraphRouter router;
    private final GraphExecutor graphExecutor;

    public TaskGraphTemplate(TaskGraphRegistry registry,
                             ContextGraphRouter router,
                             GraphExecutor graphExecutor) {
        this.registry = registry;
        this.router = router;
        this.graphExecutor = graphExecutor;
    }

    public <C> GraphExecutionResult execute(C context) {
        if (context == null) {
            throw new GraphExecutionException("context must not be null");
        }

        // 运行时只暴露 context 入口，图查找和请求组装都在门面内部完成。
        String graphId = router.route(context.getClass());
        if (graphId == null) {
            throw new GraphExecutionException(
                    "no task graph bound for context type [" + context.getClass().getName() + "]");
        }
        TaskGraph<C> taskGraph = castGraph(graphId, registry.get(graphId));
        return graphExecutor.execute(GraphExecutionRequest.<C>builder()
                .graph(taskGraph)
                .context(context)
                .build());
    }

    @SuppressWarnings("unchecked")
    private <C> TaskGraph<C> castGraph(String graphId, TaskGraph<?> taskGraph) {
        if (taskGraph == null) {
            throw new GraphExecutionException("task graph [" + graphId + "] not found");
        }
        return (TaskGraph<C>) taskGraph;
    }
}
