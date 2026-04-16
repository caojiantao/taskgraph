package io.github.caojiantao.taskgraph.kernel.validation;

import io.github.caojiantao.taskgraph.kernel.exception.GraphValidationException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;

import java.util.Map;
import java.util.Objects;

/**
 * 默认图注册期校验器，负责保证注册域内 `graphId` 唯一。
 */
public final class DefaultTaskGraphRegistryValidator implements TaskGraphRegistryValidator {

    private static final DefaultTaskGraphRegistryValidator INSTANCE = new DefaultTaskGraphRegistryValidator();

    private DefaultTaskGraphRegistryValidator() {
    }

    public static DefaultTaskGraphRegistryValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public <C> void validate(Map<String, TaskGraph<?>> graphMap, TaskGraph<C> taskGraph) {
        Objects.requireNonNull(graphMap, "graphMap must not be null");
        Objects.requireNonNull(taskGraph, "taskGraph must not be null");
        if (graphMap.containsKey(taskGraph.getGraphId())) {
            throw new GraphValidationException("graph [" + taskGraph.getGraphId() + "] already registered");
        }
    }
}
