package io.github.caojiantao.taskgraph.kernel.validation;

import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;

/**
 * 图定义校验契约。
 */
public interface TaskGraphDefinitionValidator {

    /**
     * 校验一个图定义，不合法时直接抛出异常。
     *
     * @param taskGraph 图定义
     * @param <C> 执行上下文类型
     */
    <C> void validate(TaskGraph<C> taskGraph);
}
