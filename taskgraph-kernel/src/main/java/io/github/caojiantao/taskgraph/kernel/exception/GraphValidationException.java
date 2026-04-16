package io.github.caojiantao.taskgraph.kernel.exception;

/**
 * 图定义不合法时抛出的异常。
 */
public class GraphValidationException extends ConcurrentException {

    public GraphValidationException(String message) {
        super(message);
    }

    public GraphValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
