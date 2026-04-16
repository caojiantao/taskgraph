package io.github.caojiantao.taskgraph.kernel.exception;

/**
 * TaskGraph 框架基础异常。
 */
public class ConcurrentException extends RuntimeException {

    public ConcurrentException(String message) {
        super(message);
    }

    public ConcurrentException(String message, Throwable cause) {
        super(message, cause);
    }
}
