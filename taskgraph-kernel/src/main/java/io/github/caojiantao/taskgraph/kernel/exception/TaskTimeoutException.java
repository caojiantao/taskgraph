package io.github.caojiantao.taskgraph.kernel.exception;

/**
 * 单任务超时异常。
 */
public class TaskTimeoutException extends TaskExecutionException {

    public TaskTimeoutException(String message) {
        super(message);
    }
}
