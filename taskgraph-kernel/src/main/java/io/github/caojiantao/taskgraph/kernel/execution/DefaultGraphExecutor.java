package io.github.caojiantao.taskgraph.kernel.execution;

import io.github.caojiantao.taskgraph.kernel.exception.GraphExecutionException;
import io.github.caojiantao.taskgraph.kernel.exception.TaskExecutionException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskDefinition;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.GraphRuntime;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.TaskRuntime;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.TaskRuntimeStatus;
import io.github.caojiantao.taskgraph.kernel.internal.scheduler.TaskDispatcher;
import io.github.caojiantao.taskgraph.kernel.internal.support.KernelDefaults;
import io.github.caojiantao.taskgraph.kernel.internal.timeout.TimeoutSchedulerHolder;
import io.github.caojiantao.taskgraph.kernel.result.GraphExecutionResult;
import io.github.caojiantao.taskgraph.kernel.result.GraphRuntimeState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 默认同步图执行器。
 */
public final class DefaultGraphExecutor implements GraphExecutor {

    private final ScheduledExecutorService timeoutScheduler;

    public DefaultGraphExecutor() {
        this(TimeoutSchedulerHolder.getInstance());
    }

    public DefaultGraphExecutor(ScheduledExecutorService timeoutScheduler) {
        this.timeoutScheduler = Objects.requireNonNull(timeoutScheduler, "timeoutScheduler must not be null");
    }

    @Override
    public <C> GraphExecutionResult execute(GraphExecutionRequest<C> request) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(request.getGraph(), "request graph must not be null");
        Objects.requireNonNull(request.getContext(), "request context must not be null");

        GraphRuntime<C> graphRuntime = TaskDispatcher.createRuntime(request.getGraph(), request.getContext());
        // 先提交所有根任务，再由主线程同步阻塞等待图自然完成或图级超时。
        TaskDispatcher.submitRootTasks(graphRuntime, timeoutScheduler, this);

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
            graphRuntime.markTimedOut();
            TaskDispatcher.cancelRunningTasks(graphRuntime.getTaskRuntimeMap().values());
        } else {
            resolveGraphStateOnMainThread(graphRuntime);
        }

        GraphRuntimeState state = graphRuntime.getState();
        if (state == GraphRuntimeState.RUNNING) {
            throw new GraphExecutionException("graph execution finished without a terminal state");
        }
        return GraphExecutionResult.of(state);
    }

    public <C> void runTask(GraphRuntime<C> graphRuntime, TaskRuntime<C> taskRuntime) {
        Throwable failure = null;
        try {
            if (!graphRuntime.isExecutionActive()) {
                return;
            }

            TaskDefinition<C> definition = taskRuntime.getDefinition();
            definition.getHandler().handle(graphRuntime.getContext());

            if (!taskRuntime.compareAndSetStatus(TaskRuntimeStatus.RUNNING, TaskRuntimeStatus.SUCCESS)) {
                return;
            }
            onTaskFinished(graphRuntime, taskRuntime);
            // 只有当前任务成功完成，才有资格继续释放它的下游任务。
            releaseDownstream(graphRuntime, taskRuntime);
        } catch (Throwable cause) {
            failure = cause;
            if (!graphRuntime.isExecutionActive()) {
                return;
            }
            if (!taskRuntime.compareAndSetStatus(TaskRuntimeStatus.RUNNING, TaskRuntimeStatus.FAILED)) {
                return;
            }
            handleTaskFailure(graphRuntime, taskRuntime,
                    cause instanceof TaskExecutionException ? cause : new TaskExecutionException(
                            "task [" + taskRuntime.getDefinition().getTaskId() + "] execution failed", cause));
        } finally {
            TaskDispatcher.cancelTaskWatcher(taskRuntime);
            restoreInterruptFlagIfNeeded(failure);
        }
    }

    public <C> void onTaskTimeout(GraphRuntime<C> graphRuntime, TaskRuntime<C> taskRuntime, Throwable cause) {
        handleTaskFailure(graphRuntime, taskRuntime, cause);
    }

    public <C> void onTaskSubmissionFailure(GraphRuntime<C> graphRuntime, TaskRuntime<C> taskRuntime, Throwable cause) {
        if (!graphRuntime.isExecutionActive()) {
            return;
        }
        if (!taskRuntime.compareAndSetStatus(TaskRuntimeStatus.RUNNING, TaskRuntimeStatus.FAILED)) {
            return;
        }
        handleTaskFailure(graphRuntime, taskRuntime, cause);
    }

    protected <C> void handleTaskFailure(GraphRuntime<C> graphRuntime, TaskRuntime<C> taskRuntime, Throwable cause) {
        invokeErrorHandler(graphRuntime, taskRuntime, cause);
        onTaskFinished(graphRuntime, taskRuntime);
        // 默认失败语义是“跳过失败任务的整个后继子图”，而不是立刻终止无关分支。
        skipDescendants(graphRuntime, taskRuntime.getDefinition().getTaskId());
    }

    private <C> void invokeErrorHandler(GraphRuntime<C> graphRuntime, TaskRuntime<C> taskRuntime, Throwable cause) {
        if (taskRuntime.getDefinition().getErrorHandler() == null) {
            return;
        }
        try {
            taskRuntime.getDefinition().getErrorHandler().handle(graphRuntime.getContext(), cause);
        } catch (Throwable ignored) {
            // errorHandler 属于 best-effort 回调，不能反过来打断图运行时状态收敛。
        }
    }

    private <C> void releaseDownstream(GraphRuntime<C> graphRuntime, TaskRuntime<C> taskRuntime) {
        if (!graphRuntime.isExecutionActive()) {
            return;
        }
        for (String downstreamTaskId : taskRuntime.getDownstreamTaskIds()) {
            TaskRuntime<C> downstreamRuntime = graphRuntime.getTaskRuntimeMap().get(downstreamTaskId);
            if (downstreamRuntime == null || downstreamRuntime.getStatus().get() != TaskRuntimeStatus.PENDING) {
                continue;
            }
            int remaining = downstreamRuntime.getRemainingDependencies().decrementAndGet();
            if (remaining == 0) {
                TaskDispatcher.submitTask(graphRuntime, downstreamRuntime, timeoutScheduler, this);
            }
        }
    }

    private <C> void skipDescendants(GraphRuntime<C> graphRuntime, String failedTaskId) {
        Deque<String> queue = new ArrayDeque<>();
        queue.addLast(failedTaskId);
        while (!queue.isEmpty()) {
            String currentTaskId = queue.removeFirst();
            TaskRuntime<C> currentRuntime = graphRuntime.getTaskRuntimeMap().get(currentTaskId);
            if (currentRuntime == null) {
                continue;
            }
            Set<String> downstreamTaskIds = currentRuntime.getDownstreamTaskIds();
            for (String downstreamTaskId : downstreamTaskIds) {
                TaskRuntime<C> downstreamRuntime = graphRuntime.getTaskRuntimeMap().get(downstreamTaskId);
                if (downstreamRuntime == null) {
                    continue;
                }
                // 只有还没开始执行的后继任务才会被标记成 SKIPPED，已运行中的任务不在这里强行改状态。
                if (downstreamRuntime.compareAndSetStatus(TaskRuntimeStatus.PENDING, TaskRuntimeStatus.SKIPPED)) {
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

    private <C> void onTaskFinished(GraphRuntime<C> graphRuntime, TaskRuntime<C> taskRuntime) {
        // 每个任务第一次进入运行时终态时，对整图完成计数执行一次 countDown。
        graphRuntime.getCompletionLatch().countDown();
    }

    private <C> void resolveGraphStateOnMainThread(GraphRuntime<C> graphRuntime) {
        GraphRuntimeState finalState = GraphRuntimeState.SUCCESS;
        for (TaskRuntime<C> taskRuntime : graphRuntime.getTaskRuntimeMap().values()) {
            TaskRuntimeStatus taskState = taskRuntime.getStatus().get();
            if (taskState == TaskRuntimeStatus.FAILED || taskState == TaskRuntimeStatus.SKIPPED) {
                finalState = GraphRuntimeState.DEGRADED;
                break;
            }
        }
        // 主线程在确认所有任务都已完成后，再统一汇总并写入最终图运行时状态。
        graphRuntime.compareAndSetState(GraphRuntimeState.RUNNING, finalState);
    }
}
