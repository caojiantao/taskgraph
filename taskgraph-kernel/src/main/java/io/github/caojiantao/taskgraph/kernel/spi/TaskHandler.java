package io.github.caojiantao.taskgraph.kernel.spi;

/**
 * 任务执行逻辑函数式接口。
 *
 * @param <C> 执行上下文类型
 */
@FunctionalInterface
public interface TaskHandler<C> {

    /**
     * 执行任务业务逻辑。
     *
     * @param context 执行上下文
     * @throws Exception 任务执行异常
     */
    void handle(C context) throws Exception;
}
