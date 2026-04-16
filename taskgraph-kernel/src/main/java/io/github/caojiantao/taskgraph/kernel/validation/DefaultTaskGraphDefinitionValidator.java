package io.github.caojiantao.taskgraph.kernel.validation;

import io.github.caojiantao.taskgraph.kernel.exception.GraphValidationException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskDefinition;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 默认图定义校验器，负责保证 DAG 结构与有效线程池规则成立。
 */
public final class DefaultTaskGraphDefinitionValidator implements TaskGraphDefinitionValidator {

    private static final DefaultTaskGraphDefinitionValidator INSTANCE = new DefaultTaskGraphDefinitionValidator();

    private DefaultTaskGraphDefinitionValidator() {
    }

    public static DefaultTaskGraphDefinitionValidator getInstance() {
        return INSTANCE;
    }

    @Override
    public <C> void validate(TaskGraph<C> taskGraph) {
        Objects.requireNonNull(taskGraph, "taskGraph must not be null");
        validateGraphId(taskGraph.getGraphId());
        validateGraphTimeout(taskGraph);

        Map<String, TaskDefinition<C>> taskIndex = indexTasks(taskGraph.getGraphId(), taskGraph.getTasks());
        validateNonEmptyTasks(taskGraph.getGraphId(), taskIndex);
        validateEffectiveExecutors(taskGraph, taskIndex.values());
        validateDependencies(taskGraph.getGraphId(), taskIndex);
        validateAcyclic(taskGraph.getGraphId(), taskIndex);
    }

    private void validateGraphId(String graphId) {
        if (graphId == null || graphId.trim().isEmpty()) {
            throw new GraphValidationException("graphId must not be blank");
        }
    }

    private <C> Map<String, TaskDefinition<C>> indexTasks(String graphId, List<TaskDefinition<C>> tasks) {
        Map<String, TaskDefinition<C>> taskIndex = new LinkedHashMap<>();
        if (tasks == null) {
            return taskIndex;
        }

        for (TaskDefinition<C> task : tasks) {
            if (task == null) {
                throw new GraphValidationException("graph [" + graphId + "] contains null task definition");
            }

            String taskId = task.getTaskId();
            if (taskId == null || taskId.trim().isEmpty()) {
                throw new GraphValidationException("graph [" + graphId + "] contains blank taskId");
            }
            if (taskIndex.putIfAbsent(taskId, task) != null) {
                throw new GraphValidationException("graph [" + graphId + "] contains duplicate taskId [" + taskId + "]");
            }
            validateTaskTimeout(graphId, task);
        }
        return taskIndex;
    }

    private void validateGraphTimeout(TaskGraph<?> taskGraph) {
        if (taskGraph.getTimeoutMillis() != null && taskGraph.getTimeoutMillis() <= 0L) {
            throw new GraphValidationException("graph [" + taskGraph.getGraphId() + "] timeoutMillis must be positive");
        }
    }

    private void validateNonEmptyTasks(String graphId, Map<String, ?> taskIndex) {
        if (taskIndex.isEmpty()) {
            throw new GraphValidationException("graph [" + graphId + "] must contain at least one task");
        }
    }

    private <C> void validateTaskTimeout(String graphId, TaskDefinition<C> task) {
        if (task.getTimeoutMillis() != null && task.getTimeoutMillis() <= 0L) {
            throw new GraphValidationException(
                    "graph [" + graphId + "] task [" + task.getTaskId() + "] timeoutMillis must be positive");
        }
    }

    private <C> void validateEffectiveExecutors(TaskGraph<C> taskGraph, Collection<TaskDefinition<C>> tasks) {
        for (TaskDefinition<C> task : tasks) {
            if (task.getExecutor() == null && taskGraph.getExecutor() == null) {
                throw new GraphValidationException(
                        "graph [" + taskGraph.getGraphId() + "] task [" + task.getTaskId() + "] has no effective executor");
            }
        }
    }

    private <C> void validateDependencies(String graphId, Map<String, TaskDefinition<C>> taskIndex) {
        for (TaskDefinition<C> task : taskIndex.values()) {
            for (String upstream : safeDependsOn(task)) {
                if (!taskIndex.containsKey(upstream)) {
                    throw new GraphValidationException(
                            "graph [" + graphId + "] task [" + task.getTaskId() + "] depends on missing task [" + upstream + "]");
                }
            }
        }
    }

    private <C> void validateAcyclic(String graphId, Map<String, TaskDefinition<C>> taskIndex) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> downstream = new HashMap<>();
        for (String taskId : taskIndex.keySet()) {
            inDegree.put(taskId, 0);
            downstream.put(taskId, new HashSet<>());
        }

        for (TaskDefinition<C> task : taskIndex.values()) {
            for (String upstream : safeDependsOn(task)) {
                downstream.get(upstream).add(task.getTaskId());
                inDegree.put(task.getTaskId(), inDegree.get(task.getTaskId()) + 1);
            }
        }

        Deque<String> readyQueue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                readyQueue.add(entry.getKey());
            }
        }

        int visited = 0;
        while (!readyQueue.isEmpty()) {
            String current = readyQueue.removeFirst();
            visited++;
            for (String next : downstream.get(current)) {
                int nextDegree = inDegree.get(next) - 1;
                inDegree.put(next, nextDegree);
                if (nextDegree == 0) {
                    readyQueue.addLast(next);
                }
            }
        }

        if (visited != taskIndex.size()) {
            throw new GraphValidationException("graph [" + graphId + "] contains cyclic dependencies");
        }
    }

    private <C> Set<String> safeDependsOn(TaskDefinition<C> task) {
        Set<String> dependsOn = task.getDependsOn();
        return dependsOn == null ? java.util.Collections.emptySet() : dependsOn;
    }
}
