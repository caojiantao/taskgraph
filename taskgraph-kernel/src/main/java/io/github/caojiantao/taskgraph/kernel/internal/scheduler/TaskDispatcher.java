package io.github.caojiantao.taskgraph.kernel.internal.scheduler;

import io.github.caojiantao.taskgraph.kernel.execution.DefaultGraphExecutor;
import io.github.caojiantao.taskgraph.kernel.exception.TaskExecutionException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskNode;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.GraphRuntime;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.TaskNodeRuntime;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.TaskNodeRuntimeStatus;
import io.github.caojiantao.taskgraph.kernel.internal.support.KernelDefaults;
import io.github.caojiantao.taskgraph.kernel.internal.timeout.TaskTimeoutWatcher;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 内部任务分发器，负责运行时构建与任务提交。
 */
public final class TaskDispatcher {

    private TaskDispatcher() {
    }

    public static <C> GraphRuntime<C> createRuntime(TaskGraph<C> graph, C context) {
        Map<String, Set<String>> downstreamMap = buildDownstreamMap(graph.getTasks());
        Map<String, TaskNodeRuntime<C>> taskNodeRuntimeMap = new LinkedHashMap<>();
        for (TaskNode<C> taskNode : graph.getTasks()) {
            Set<String> downstreamTaskIds = downstreamMap.get(taskNode.getTaskId());
            Set<String> immutableDownstream = downstreamTaskIds == null
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(downstreamTaskIds));
            int dependencyCount = taskNode.getDependsOn() == null ? 0 : taskNode.getDependsOn().size();
            taskNodeRuntimeMap.put(taskNode.getTaskId(), new TaskNodeRuntime<>(taskNode, dependencyCount, immutableDownstream));
        }
        // 运行期状态与定义期图对象彻底分离，保证同一图可重复执行且互不污染。
        return new GraphRuntime<>(graph, context, taskNodeRuntimeMap);
    }

    public static <C> void submitRootTasks(GraphRuntime<C> graphRuntime,
                                           ScheduledExecutorService timeoutScheduler,
                                           DefaultGraphExecutor executor) {
        for (TaskNodeRuntime<C> taskRuntime : graphRuntime.getTaskNodeRuntimeMap().values()) {
            // 根任务的剩余依赖数为 0，可以在图启动时直接提交。
            if (taskRuntime.getRemainingDependencies().get() == 0) {
                submitTask(graphRuntime, taskRuntime, timeoutScheduler, executor);
            }
        }
    }

    public static <C> void submitTask(GraphRuntime<C> graphRuntime,
                                      TaskNodeRuntime<C> taskRuntime,
                                      ScheduledExecutorService timeoutScheduler,
                                      DefaultGraphExecutor executor) {
        if (!graphRuntime.isExecutionActive()) {
            return;
        }
        if (!taskRuntime.compareAndSetStatus(TaskNodeRuntimeStatus.PENDING, TaskNodeRuntimeStatus.RUNNING)) {
            return;
        }

        TaskNode<C> taskNode = taskRuntime.getTaskNode();
        ExecutorService effectiveExecutor = taskNode.getExecutor() != null
                ? taskNode.getExecutor()
                : graphRuntime.getGraph().getExecutor();

        Future<?> future = null;
        try {
            Runnable taskRunnable = () -> executor.runTask(graphRuntime, taskRuntime);
            future = effectiveExecutor.submit(taskRunnable);
            taskRuntime.setFuture(future);

            long timeoutMillis = taskNode.getTimeoutMillis() != null
                    ? taskNode.getTimeoutMillis()
                    : KernelDefaults.DEFAULT_TASK_TIMEOUT_MILLIS;
            // 提交任务后立即注册任务级超时 watcher，但 watcher 本身只做轻量取消与回调。
            TaskTimeoutWatcher<C> watcher = new TaskTimeoutWatcher<>(graphRuntime, taskRuntime,
                    (runtime, runtimeTask, cause) -> executor.onTaskTimeout(runtime, runtimeTask, cause));
            ScheduledFuture<?> timeoutWatcher = timeoutScheduler.schedule(watcher, timeoutMillis, TimeUnit.MILLISECONDS);
            taskRuntime.setTimeoutWatcher(timeoutWatcher);
        } catch (RejectedExecutionException ex) {
            cancelFutureIfNecessary(future);
            executor.onTaskSubmissionFailure(graphRuntime, taskRuntime,
                    new TaskExecutionException("task [" + taskNode.getTaskId() + "] submission rejected", ex));
        } catch (RuntimeException ex) {
            cancelFutureIfNecessary(future);
            executor.onTaskSubmissionFailure(graphRuntime, taskRuntime,
                    new TaskExecutionException("task [" + taskNode.getTaskId() + "] submission failed", ex));
        }
    }

    public static <C> void cancelTaskWatcher(TaskNodeRuntime<C> taskRuntime) {
        ScheduledFuture<?> timeoutWatcher = taskRuntime.getTimeoutWatcher();
        if (timeoutWatcher != null) {
            timeoutWatcher.cancel(false);
        }
    }

    public static <C> void cancelRunningTasks(Collection<TaskNodeRuntime<C>> taskRuntimes) {
        for (TaskNodeRuntime<C> taskRuntime : taskRuntimes) {
            cancelTaskWatcher(taskRuntime);
            Future<?> future = taskRuntime.getFuture();
            if (future != null && !future.isDone()) {
                // 图级超时时，对仍未完成的任务统一发出中断取消请求。
                future.cancel(true);
            }
        }
    }

    private static void cancelFutureIfNecessary(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    private static <C> Map<String, Set<String>> buildDownstreamMap(java.util.List<TaskNode<C>> taskNodes) {
        Map<String, Set<String>> downstreamMap = new LinkedHashMap<>();
        for (TaskNode<C> taskNode : taskNodes) {
            downstreamMap.put(taskNode.getTaskId(), new LinkedHashSet<>());
        }
        for (TaskNode<C> taskNode : taskNodes) {
            if (taskNode.getDependsOn() == null) {
                continue;
            }
            for (String upstream : taskNode.getDependsOn()) {
                downstreamMap.get(upstream).add(taskNode.getTaskId());
            }
        }
        return downstreamMap;
    }
}
