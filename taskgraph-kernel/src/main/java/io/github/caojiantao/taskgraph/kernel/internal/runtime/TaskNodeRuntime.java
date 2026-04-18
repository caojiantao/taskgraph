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
    // 记录任务成功提交到线程池的时刻，用于计算排队耗时。
    private volatile long submittedNanoTime;
    // 记录任务真正进入执行线程的时刻，用于计算执行耗时。
    private volatile long startedNanoTime;

    public TaskNodeRuntime(TaskNode<C> taskNode, int remainingDependencies, Set<String> downstreamTaskIds) {
        this.taskNode = taskNode;
        this.downstreamTaskIds = downstreamTaskIds;
        this.remainingDependencies = new AtomicInteger(remainingDependencies);
        this.status = new AtomicReference<>(TaskNodeRuntimeStatus.PENDING);
        this.futureRef = new AtomicReference<>();
        this.timeoutWatcherRef = new AtomicReference<>();
        this.submittedNanoTime = -1L;
        this.startedNanoTime = -1L;
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

    public void markSubmitted(long submittedNanoTime) {
        // 这里代表“已经进入线程池调度范围”，但还不代表任务已经开始执行。
        this.submittedNanoTime = submittedNanoTime;
    }

    public void markStarted(long startedNanoTime) {
        // 只有真正开始执行 handler 时才会写入这个时间点。
        this.startedNanoTime = startedNanoTime;
    }
}
