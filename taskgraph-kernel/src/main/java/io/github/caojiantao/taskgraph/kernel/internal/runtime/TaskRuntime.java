package io.github.caojiantao.taskgraph.kernel.internal.runtime;

import io.github.caojiantao.taskgraph.kernel.graph.TaskDefinition;
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
public final class TaskRuntime<C> {

    private final TaskDefinition<C> definition;
    private final Set<String> downstreamTaskIds;
    private final AtomicInteger remainingDependencies;
    private final AtomicReference<TaskRuntimeStatus> status;
    private final AtomicReference<Future<?>> futureRef;
    private final AtomicReference<ScheduledFuture<?>> timeoutWatcherRef;

    public TaskRuntime(TaskDefinition<C> definition, int remainingDependencies, Set<String> downstreamTaskIds) {
        this.definition = definition;
        this.downstreamTaskIds = downstreamTaskIds;
        this.remainingDependencies = new AtomicInteger(remainingDependencies);
        this.status = new AtomicReference<>(TaskRuntimeStatus.PENDING);
        this.futureRef = new AtomicReference<>();
        this.timeoutWatcherRef = new AtomicReference<>();
    }

    public boolean compareAndSetStatus(TaskRuntimeStatus expected, TaskRuntimeStatus update) {
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
