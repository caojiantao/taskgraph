package io.github.caojiantao.taskgraph.kernel.spi;

/**
 * 任务失败回调函数式接口。
 *
 * @param <C> 执行上下文类型
 */
@FunctionalInterface
public interface TaskErrorHandler<C> {

    /**
     * 处理一次任务失败。
     *
     * @param context 执行上下文
     * @param cause 失败原因
     */
    void handle(C context, Throwable cause);
}
