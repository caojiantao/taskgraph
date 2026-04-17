package io.github.caojiantao.taskgraph.kernel.graph;

import io.github.caojiantao.taskgraph.kernel.validation.DefaultTaskGraphRegistryValidator;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图注册表。
 * 在同一个注册表实例内，`graphId` 必须全局唯一。
 */
public final class TaskGraphRegistry {

    private final Map<String, TaskGraph<?>> graphMap = new ConcurrentHashMap<>();

    /**
     * 注册一张图。若同一注册表中已存在相同 `graphId`，则拒绝注册。
     *
     * @param taskGraph 图定义
     * @param <C> 执行上下文类型
     * @return 原图对象，便于链式使用
     */
    public <C> TaskGraph<C> register(TaskGraph<C> taskGraph) {
        Objects.requireNonNull(taskGraph, "taskGraph must not be null");
        DefaultTaskGraphRegistryValidator.getInstance().validate(graphMap, taskGraph);
        graphMap.put(taskGraph.getGraphId(), taskGraph);
        return taskGraph;
    }

    /**
     * 判断指定 `graphId` 是否已注册。
     *
     * @param graphId 图标识
     * @return 是否已存在
     */
    public boolean contains(String graphId) {
        Objects.requireNonNull(graphId, "graphId must not be null");
        return graphMap.containsKey(graphId);
    }

    /**
     * 返回指定 `graphId` 对应的图定义；若不存在则返回 `null`。
     *
     * @param graphId 图标识
     * @return 图定义或 `null`
     */
    public TaskGraph<?> get(String graphId) {
        Objects.requireNonNull(graphId, "graphId must not be null");
        return graphMap.get(graphId);
    }

    /**
     * 返回当前注册表中的全部图定义快照。
     *
     * @return 不可变图定义集合
     */
    public Collection<TaskGraph<?>> getAll() {
        return Collections.unmodifiableCollection(graphMap.values());
    }
}
