package io.github.caojiantao.taskgraph.kernel.internal.timeout;

import io.github.caojiantao.taskgraph.kernel.exception.TaskExecutionException;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.GraphRuntime;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.TaskRuntime;
import io.github.caojiantao.taskgraph.kernel.internal.runtime.TaskRuntimeStatus;

import java.util.concurrent.Future;

/**
 * 单任务超时观察回调。
 */
public final class TaskTimeoutWatcher<C> implements Runnable {

    private final GraphRuntime<C> graphRuntime;
    private final TaskRuntime<C> taskRuntime;
    private final TaskTimeoutHandler<C> timeoutHandler;

    public TaskTimeoutWatcher(GraphRuntime<C> graphRuntime,
                              TaskRuntime<C> taskRuntime,
                              TaskTimeoutHandler<C> timeoutHandler) {
        this.graphRuntime = graphRuntime;
        this.taskRuntime = taskRuntime;
        this.timeoutHandler = timeoutHandler;
    }

    @Override
    public void run() {
        if (!graphRuntime.isExecutionActive()) {
            return;
        }
        if (!taskRuntime.compareAndSetStatus(TaskRuntimeStatus.RUNNING, TaskRuntimeStatus.FAILED)) {
            return;
        }

        Future<?> future = taskRuntime.getFuture();
        if (future != null) {
            // 超时发生后通过 cancel(true) 发出中断请求，而不是强杀线程。
            future.cancel(true);
        }
        timeoutHandler.onTaskTimeout(graphRuntime, taskRuntime,
                new TaskExecutionException("task [" + taskRuntime.getDefinition().getTaskId() + "] timed out"));
    }

    /**
     * 用于把超时失败回传给执行器的回调。
     */
    public interface TaskTimeoutHandler<C> {

        void onTaskTimeout(GraphRuntime<C> graphRuntime, TaskRuntime<C> taskRuntime, Throwable cause);
    }
}
