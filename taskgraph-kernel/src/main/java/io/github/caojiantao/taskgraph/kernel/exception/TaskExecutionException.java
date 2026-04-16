package io.github.caojiantao.taskgraph.kernel.exception;

/**
 * 单任务执行失败的内部异常。
 */
public class TaskExecutionException extends GraphExecutionException {

    public TaskExecutionException(String message) {
        super(message);
    }

    public TaskExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
