package io.github.caojiantao.taskgraph.kernel.validation;

import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;

import java.util.Map;

/**
 * 图注册期校验契约。
 */
public interface TaskGraphRegistryValidator {

    /**
     * 校验图在当前注册范围内是否允许注册。
     *
     * @param graphMap 当前注册表快照
     * @param taskGraph 待注册图定义
     * @param <C> 执行上下文类型
     */
    <C> void validate(Map<String, TaskGraph<?>> graphMap, TaskGraph<C> taskGraph);
}
