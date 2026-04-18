package io.github.caojiantao.taskgraph.kernel.execution;

import io.github.caojiantao.taskgraph.kernel.exception.GraphExecutionException;
import io.github.caojiantao.taskgraph.kernel.exception.TaskExecutionException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskNode;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.GraphRuntime;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.TaskNodeRuntime;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.TaskNodeRuntimeStatus;
import io.github.caojiantao.taskgraph.kernel.internal.scheduler.TaskDispatcher;
import io.github.caojiantao.taskgraph.kernel.internal.support.KernelDefaults;
import io.github.caojiantao.taskgraph.kernel.internal.timeout.TaskTimeoutWatcher;
import io.github.caojiantao.taskgraph.kernel.internal.timeout.TimeoutSchedulerHolder;
import io.github.caojiantao.taskgraph.kernel.observation.GraphObservationDispatcher;
import io.github.caojiantao.taskgraph.kernel.observation.NoOpGraphObservationDispatcher;
import io.github.caojiantao.taskgraph.kernel.observation.event.graph.GraphFinishedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.graph.GraphStartedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.graph.GraphTimedOutEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskFailedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskSkippedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskStartedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskSubmissionFailedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskSucceededEvent;
import io.github.caojiantao.taskgraph.kernel.result.GraphExecutionResult;
import io.github.caojiantao.taskgraph.kernel.result.GraphRuntimeState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * 默认同步图执行器。
 */
public final class DefaultGraphExecutor implements GraphExecutor {

    private final ScheduledExecutorService timeoutScheduler;
    private final GraphObservationDispatcher observationDispatcher;

    public DefaultGraphExecutor() {
        this(TimeoutSchedulerHolder.getInstance(), NoOpGraphObservationDispatcher.getInstance());
    }

    public DefaultGraphExecutor(GraphObservationDispatcher observationDispatcher) {
        this(TimeoutSchedulerHolder.getInstance(), observationDispatcher);
    }

    public DefaultGraphExecutor(ScheduledExecutorService timeoutScheduler) {
        this(timeoutScheduler, NoOpGraphObservationDispatcher.getInstance());
    }

    public DefaultGraphExecutor(ScheduledExecutorService timeoutScheduler,
                                GraphObservationDispatcher observationDispatcher) {
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler must not be null");
        this.observationDispatcher = Objects.requireNonNull(observationDispatcher,
                "observationDispatcher must not be null");
    }

    @Override
    public <C> GraphExecutionResult execute(GraphExecutionRequest<C> request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getGraph(), "request graph must not be null");
        Objects.requireNonNull(request.getContext(), "request context must not be null");

        String executionId = UUID.randomUUID().toString();
        long graphStartNanoTime = System.nanoTime();
        GraphRuntime<C> graphRuntime = TaskDispatcher.createRuntime(executionId,
                request.getGraph(),
                request.getContext(),
                graphStartNanoTime);
        publishGraphStarted(graphRuntime);
        // 先提交所有根任务，再由主线程同步阻塞等待图自然完成或图级超时。
        TaskDispatcher.submitRootTasks(graphRuntime, this);

        long graphTimeoutMillis = request.getGraph().getTimeoutMillis() != null
                ? request.getGraph().getTimeoutMillis()
                : KernelDefaults.DEFAULT_GRAPH_TIMEOUT_MILLIS;

        boolean completed;
        try {
            completed = graphRuntime.getCompletionLatch().await(graphTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GraphExecutionException("graph execution interrupted", ex);
        }

        if (!completed) {
            // 图级超时一旦发生，先收敛图运行时状态，再尽力取消尚未完成的任务。
            if (graphRuntime.markTimedOut()) {
                publishGraphTimedOut(graphRuntime, Duration.ofMillis(graphTimeoutMillis));
            }
            TaskDispatcher.cancelRunningTasks(graphRuntime.getTaskNodeRuntimeMap().values());
        } else {
            resolveGraphStateOnMainThread(graphRuntime);
        }

        GraphRuntimeState state = graphRuntime.getState();
        if (state == GraphRuntimeState.RUNNING) {
            throw new GraphExecutionException("graph execution finished without a terminal state");
        }
        publishGraphFinished(graphRuntime, state);
        return GraphExecutionResult.of(state);
    }

    public <C> void runTask(GraphRuntime<C> graphRuntime, TaskNodeRuntime<C> taskRuntime) {
        Throwable failure = null;
        try {
            if (!graphRuntime.isExecutionActive()) {
                return;
            }

            // 任务真正进入执行线程后再记录起始时间，这样事件 duration 只表示实际执行耗时。
            taskRuntime.markStarted(System.nanoTime());
            TaskNode<C> taskNode = taskRuntime.getTaskNode();
            // 开始事件除了表达“开始执行”，还会顺带对外暴露排队耗时。
            publishTaskStarted(graphRuntime, taskNode.getTaskId());
            registerTaskTimeoutWatcher(graphRuntime, taskRuntime);
            taskNode.getHandler().handle(graphRuntime.getContext());

            if (!taskRuntime.compareAndSetStatus(TaskNodeRuntimeStatus.RUNNING, TaskNodeRuntimeStatus.SUCCESS)) {
                return;
            }
            publishTaskSucceeded(graphRuntime, taskRuntime);
            onTaskFinished(graphRuntime, taskRuntime);
            // 只有当前任务成功完成，才有资格继续释放它的下游任务。
            releaseDownstream(graphRuntime, taskRuntime);
        } catch (Throwable cause) {
            failure = cause;
            if (!graphRuntime.isExecutionActive()) {
                return;
            }
            if (!taskRuntime.compareAndSetStatus(TaskNodeRuntimeStatus.RUNNING, TaskNodeRuntimeStatus.FAILED)) {
                return;
            }
            handleTaskExecutionFailure(graphRuntime, taskRuntime,
                    cause instanceof TaskExecutionException ? cause : new TaskExecutionException(
                            "task [" + taskRuntime.getTaskNode().getTaskId() + "] execution failed", cause));
        } finally {
            TaskDispatcher.cancelTaskWatcher(taskRuntime);
            restoreInterruptFlagIfNeeded(failure);
        }
    }

    public <C> void onTaskTimeout(GraphRuntime<C> graphRuntime, TaskNodeRuntime<C> taskRuntime, Throwable cause) {
        handleTaskExecutionFailure(graphRuntime, taskRuntime, cause);
    }

    public <C> void onTaskSubmissionFailure(GraphRuntime<C> graphRuntime, TaskNodeRuntime<C> taskRuntime, Throwable cause) {
        if (!graphRuntime.isExecutionActive()) {
            return;
        }
        if (!taskRuntime.compareAndSetStatus(TaskNodeRuntimeStatus.RUNNING, TaskNodeRuntimeStatus.FAILED)) {
            return;
        }
        publishTaskSubmissionFailed(graphRuntime, taskRuntime, cause);
        invokeErrorHandler(graphRuntime, taskRuntime, cause);
        onTaskFinished(graphRuntime, taskRuntime);
        skipDescendants(graphRuntime, taskRuntime.getTaskNode().getTaskId());
    }

    protected <C> void handleTaskExecutionFailure(GraphRuntime<C> graphRuntime,
                                                  TaskNodeRuntime<C> taskRuntime,
                                                  Throwable cause) {
        publishTaskFailed(graphRuntime, taskRuntime, cause);
        invokeErrorHandler(graphRuntime, taskRuntime, cause);
        onTaskFinished(graphRuntime, taskRuntime);
        // 默认失败语义是“跳过失败任务的整个后继子图”，而不是立刻终止无关分支。
        skipDescendants(graphRuntime, taskRuntime.getTaskNode().getTaskId());
    }

    private <C> void invokeErrorHandler(GraphRuntime<C> graphRuntime, TaskNodeRuntime<C> taskRuntime, Throwable cause) {
        if (taskRuntime.getTaskNode().getErrorHandler() == null) {
            return;
        }
        try {
            taskRuntime.getTaskNode().getErrorHandler().handle(graphRuntime.getContext(), cause);
        } catch (Throwable ignored) {
            // errorHandler 属于 best-effort 回调，不能反过来打断图运行时状态收敛。
        }
    }

    private <C> void releaseDownstream(GraphRuntime<C> graphRuntime, TaskNodeRuntime<C> taskRuntime) {
        if (!graphRuntime.isExecutionActive()) {
            return;
        }
        for (String downstreamTaskId : taskRuntime.getDownstreamTaskIds()) {
            TaskNodeRuntime<C> downstreamRuntime = graphRuntime.getTaskNodeRuntimeMap().get(downstreamTaskId);
            if (downstreamRuntime == null || downstreamRuntime.getStatus().get() != TaskNodeRuntimeStatus.PENDING) {
                continue;
            }
            int remaining = downstreamRuntime.getRemainingDependencies().decrementAndGet();
            if (remaining == 0) {
                TaskDispatcher.submitTask(graphRuntime, downstreamRuntime, this);
            }
        }
    }

    private <C> void skipDescendants(GraphRuntime<C> graphRuntime, String failedTaskId) {
        Deque<String> queue = new ArrayDeque<>();
        queue.addLast(failedTaskId);
        while (!queue.isEmpty()) {
            String currentTaskId = queue.removeFirst();
            TaskNodeRuntime<C> currentRuntime = graphRuntime.getTaskNodeRuntimeMap().get(currentTaskId);
            if (currentRuntime == null) {
                continue;
            }
            Set<String> downstreamTaskIds = currentRuntime.getDownstreamTaskIds();
            for (String downstreamTaskId : downstreamTaskIds) {
                TaskNodeRuntime<C> downstreamRuntime = graphRuntime.getTaskNodeRuntimeMap().get(downstreamTaskId);
                if (downstreamRuntime == null) {
                    continue;
                }
                // 只有还没开始执行的后继任务才会被标记成 SKIPPED，已运行中的任务不在这里强行改状态。
                if (downstreamRuntime.compareAndSetStatus(TaskNodeRuntimeStatus.PENDING, TaskNodeRuntimeStatus.SKIPPED)) {
                    publishTaskSkipped(graphRuntime, downstreamTaskId, currentTaskId);
                    onTaskFinished(graphRuntime, downstreamRuntime);
                    queue.addLast(downstreamTaskId);
                }
            }
        }
    }

    private void restoreInterruptFlagIfNeeded(Throwable cause) {
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private <C> void onTaskFinished(GraphRuntime<C> graphRuntime, TaskNodeRuntime<C> taskRuntime) {
        // 每个任务第一次进入运行时终态时，对整图完成计数执行一次 countDown。
        graphRuntime.getCompletionLatch().countDown();
    }

    private <C> void registerTaskTimeoutWatcher(GraphRuntime<C> graphRuntime, TaskNodeRuntime<C> taskRuntime) {
        Long timeoutMillis = taskRuntime.getTaskNode().getTimeoutMillis();
        long effectiveTimeoutMillis = timeoutMillis != null
                ? timeoutMillis
                : KernelDefaults.DEFAULT_TASK_TIMEOUT_MILLIS;
        // 任务 watcher 在真正进入执行线程后再启动，这样任务超时只统计实际执行耗时。
        TaskTimeoutWatcher<C> watcher = new TaskTimeoutWatcher<>(graphRuntime, taskRuntime,
                (runtime, runtimeTask, cause) -> onTaskTimeout(runtime, runtimeTask, cause));
        ScheduledFuture<?> timeoutWatcher =
                timeoutScheduler.schedule(watcher, effectiveTimeoutMillis, TimeUnit.MILLISECONDS);
        taskRuntime.setTimeoutWatcher(timeoutWatcher);
    }

    private <C> void resolveGraphStateOnMainThread(GraphRuntime<C> graphRuntime) {
        GraphRuntimeState finalState = GraphRuntimeState.SUCCESS;
        for (TaskNodeRuntime<C> taskRuntime : graphRuntime.getTaskNodeRuntimeMap().values()) {
            TaskNodeRuntimeStatus taskState = taskRuntime.getStatus().get();
            if (taskState == TaskNodeRuntimeStatus.FAILED || taskState == TaskNodeRuntimeStatus.SKIPPED) {
                finalState = GraphRuntimeState.DEGRADED;
                break;
            }
        }
        // 主线程在确认所有任务都已完成后，再统一汇总并写入最终图运行时状态。
        graphRuntime.compareAndSetState(GraphRuntimeState.RUNNING, finalState);
    }

    private <C> void publishGraphStarted(GraphRuntime<C> graphRuntime) {
        observationDispatcher.dispatch(new GraphStartedEvent(
                graphRuntime.getExecutionId(),
                graphRuntime.getGraph().getGraphId(),
                Instant.now()));
    }

    private <C> void publishGraphTimedOut(GraphRuntime<C> graphRuntime, Duration timeout) {
        observationDispatcher.dispatch(new GraphTimedOutEvent(
                graphRuntime.getExecutionId(),
                graphRuntime.getGraph().getGraphId(),
                Instant.now(),
                timeout));
    }

    private <C> void publishGraphFinished(GraphRuntime<C> graphRuntime, GraphRuntimeState state) {
        observationDispatcher.dispatch(new GraphFinishedEvent(
                graphRuntime.getExecutionId(),
                graphRuntime.getGraph().getGraphId(),
                Instant.now(),
                state,
                Duration.ofNanos(System.nanoTime() - graphRuntime.getGraphStartNanoTime())));
    }

    private <C> void publishTaskStarted(GraphRuntime<C> graphRuntime, String taskId) {
        TaskNodeRuntime<C> taskRuntime = graphRuntime.getTaskNodeRuntimeMap().get(taskId);
        observationDispatcher.dispatch(new TaskStartedEvent(
                graphRuntime.getExecutionId(),
                graphRuntime.getGraph().getGraphId(),
                Instant.now(),
                taskId,
                taskQueueDuration(taskRuntime)));
    }

    private <C> void publishTaskSucceeded(GraphRuntime<C> graphRuntime, TaskNodeRuntime<C> taskRuntime) {
        observationDispatcher.dispatch(new TaskSucceededEvent(
                graphRuntime.getExecutionId(),
                graphRuntime.getGraph().getGraphId(),
                Instant.now(),
                taskRuntime.getTaskNode().getTaskId(),
                taskDuration(taskRuntime)));
    }

    private <C> void publishTaskFailed(GraphRuntime<C> graphRuntime, TaskNodeRuntime<C> taskRuntime, Throwable cause) {
        observationDispatcher.dispatch(new TaskFailedEvent(
                graphRuntime.getExecutionId(),
                graphRuntime.getGraph().getGraphId(),
                Instant.now(),
                taskRuntime.getTaskNode().getTaskId(),
                cause,
                taskDuration(taskRuntime)));
    }

    private <C> void publishTaskSubmissionFailed(GraphRuntime<C> graphRuntime,
                                                 TaskNodeRuntime<C> taskRuntime,
                                                 Throwable cause) {
        observationDispatcher.dispatch(new TaskSubmissionFailedEvent(
                graphRuntime.getExecutionId(),
                graphRuntime.getGraph().getGraphId(),
                Instant.now(),
                taskRuntime.getTaskNode().getTaskId(),
                cause));
    }

    private <C> void publishTaskSkipped(GraphRuntime<C> graphRuntime, String taskId, String triggeredByTaskId) {
        observationDispatcher.dispatch(new TaskSkippedEvent(
                graphRuntime.getExecutionId(),
                graphRuntime.getGraph().getGraphId(),
                Instant.now(),
                taskId,
                triggeredByTaskId));
    }

    private <C> Duration taskDuration(TaskNodeRuntime<C> taskRuntime) {
        long startedNanoTime = taskRuntime.getStartedNanoTime();
        if (startedNanoTime < 0L) {
            return Duration.ZERO;
        }
        // 这里只统计真正开始执行后的耗时，不把线程池排队时间算进去。
        return Duration.ofNanos(System.nanoTime() - startedNanoTime);
    }

    private <C> Duration taskQueueDuration(TaskNodeRuntime<C> taskRuntime) {
        if (taskRuntime == null) {
            return Duration.ZERO;
        }
        long submittedNanoTime = taskRuntime.getSubmittedNanoTime();
        long startedNanoTime = taskRuntime.getStartedNanoTime();
        if (submittedNanoTime < 0L || startedNanoTime < 0L || startedNanoTime < submittedNanoTime) {
            return Duration.ZERO;
        }
        // 排队耗时 = 提交进入线程池调度后，到真正拿到执行线程之间的时间差。
        return Duration.ofNanos(startedNanoTime - submittedNanoTime);
    }
}
