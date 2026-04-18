package io.github.caojiantao.taskgraph.kernel.internal.runtime;

import io.github.caojiantao.taskgraph.kernel.graph.TaskNode;
import lombok.Getter;

import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单次图执行中的任务运行时状态。
 *
 * @param <C> 执行上下文类型
 */
@Getter
public final class TaskNodeRuntime<C> {

    private final TaskNode<C> taskNode;
    private final Set<String> downstreamTaskIds;
    private final AtomicInteger remainingDependencies;
    private final AtomicReference<TaskNodeRuntimeStatus> status;
    private final AtomicReference<Future<?>> futureRef;
    private final AtomicReference<ScheduledFuture<?>> timeoutWatcherRef;

    public TaskNodeRuntime(TaskNode<C> taskNode, int remainingDependencies, Set<String> downstreamTaskIds) {
        this.taskNode = taskNode;
        this.downstreamTaskIds = downstreamTaskIds;
        this.remainingDependencies = new AtomicInteger(remainingDependencies);
        this.status = new AtomicReference<>(TaskNodeRuntimeStatus.PENDING);
        this.futureRef = new AtomicReference<>();
        this.timeoutWatcherRef = new AtomicReference<>();
    }

    public boolean compareAndSetStatus(TaskNodeRuntimeStatus expected, TaskNodeRuntimeStatus update) {
        return status.compareAndSet(expected, update);
    }

    public void setFuture(Future<?> future) {
        futureRef.set(future);
    }

    public Future<?> getFuture() {
        return futureRef.get();
    }

    public void setTimeoutWatcher(ScheduledFuture<?> timeoutWatcher) {
        timeoutWatcherRef.set(timeoutWatcher);
    }

    public ScheduledFuture<?> getTimeoutWatcher() {
        return timeoutWatcherRef.get();
    }
}
