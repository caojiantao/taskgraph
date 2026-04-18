package io.github.caojiantao.taskgraph.kernel.validation;

import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;

/**
 * 图模型校验契约。
 */
public interface TaskGraphValidator {

    /**
     * 校验一个图模型，不合法时直接抛出异常。
     *
     * @param taskGraph 图模型
     * @param <C> 执行上下文类型
     */
    <C> void validate(TaskGraph<C> taskGraph);
}
