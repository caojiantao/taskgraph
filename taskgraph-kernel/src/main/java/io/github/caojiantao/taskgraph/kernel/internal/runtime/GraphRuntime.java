package io.github.caojiantao.taskgraph.kernel.internal.runtime;

import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import io.github.caojiantao.taskgraph.kernel.result.GraphRuntimeState;
import lombok.Getter;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单次同步图执行的内部运行时状态。
 *
 * @param <C> 执行上下文类型
 */
@Getter
public final class GraphRuntime<C> {

    private final String executionId;
    private final TaskGraph<C> graph;
    private final C context;
    private final long graphStartNanoTime;
    private final Map<String, TaskNodeRuntime<C>> taskNodeRuntimeMap;
    private final CountDownLatch completionLatch;
    private final AtomicReference<GraphRuntimeState> state;

    public GraphRuntime(String executionId,
                        TaskGraph<C> graph,
                        C context,
                        long graphStartNanoTime,
                        Map<String, TaskNodeRuntime<C>> taskNodeRuntimeMap) {
        this.executionId = executionId;
        this.graph = graph;
        this.context = context;
        this.graphStartNanoTime = graphStartNanoTime;
        this.taskNodeRuntimeMap = taskNodeRuntimeMap;
        this.completionLatch = new CountDownLatch(taskNodeRuntimeMap.size());
        this.state = new AtomicReference<>(GraphRuntimeState.RUNNING);
    }

    public GraphRuntimeState getState() {
        return state.get();
    }

    public boolean compareAndSetState(GraphRuntimeState expected, GraphRuntimeState update) {
        return state.compareAndSet(expected, update);
    }

    /**
     * 图仍处于活跃运行区间时，允许继续释放和提交无关分支任务。
     */
    public boolean isExecutionActive() {
        return state.get() == GraphRuntimeState.RUNNING;
    }

    public boolean markTimedOut() {
        if (compareAndSetState(GraphRuntimeState.RUNNING, GraphRuntimeState.TIMED_OUT)) {
            // 图级超时一旦抢占成功，就直接固定图运行时状态。
            return true;
        }
        return false;
    }
}
