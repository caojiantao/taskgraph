package io.github.caojiantao.taskgraph.kernel.exception;

/**
 * 图执行阶段基础异常。
 */
public class GraphExecutionException extends ConcurrentException {

    public GraphExecutionException(String message) {
        super(message);
    }

    public GraphExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
