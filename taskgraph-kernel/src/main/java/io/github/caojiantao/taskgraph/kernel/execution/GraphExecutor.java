package io.github.caojiantao.taskgraph.kernel.execution;

import io.github.caojiantao.taskgraph.kernel.result.GraphExecutionResult;

/**
 * 图同步执行入口。
 */
public interface GraphExecutor {

    /**
     * 执行一次图请求，并返回结构化图结果。
     *
     * @param request 不可变执行请求
     * @param <C> 执行上下文类型
     * @return 图级执行结果
     */
    <C> GraphExecutionResult execute(GraphExecutionRequest<C> request);
}
