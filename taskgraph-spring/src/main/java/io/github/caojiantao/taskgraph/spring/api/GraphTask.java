package io.github.caojiantao.taskgraph.spring.api;

/**
 * Spring 侧任务执行契约。
 *
 * @param <C> 上下文类型
 */
public interface GraphTask<C> {

    /**
     * 执行任务逻辑。
     *
     * @param context 执行上下文
     * @throws Exception 执行异常
     */
    void handle(C context) throws Exception;

    /**
     * 处理任务失败。
     *
     * @param context 执行上下文
     * @param cause 失败原因
     */
    default void onError(C context, Throwable cause) {
    }
}
