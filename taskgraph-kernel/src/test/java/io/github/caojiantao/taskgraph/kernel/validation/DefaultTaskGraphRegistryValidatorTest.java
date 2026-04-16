package io.github.caojiantao.taskgraph.kernel.validation;

import io.github.caojiantao.taskgraph.kernel.exception.GraphValidationException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskDefinition;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultTaskGraphRegistryValidatorTest {

    @Test
    void shouldRejectDuplicateGraphIdInSameRegistryScope() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Map<String, TaskGraph<?>> graphMap = new ConcurrentHashMap<>();
            TaskGraph<Object> graph = taskGraph(executor, "detail-page", "product");
            graphMap.put(graph.getGraphId(), graph);

            assertThatThrownBy(() -> DefaultTaskGraphRegistryValidator.getInstance()
                    .validate(graphMap, taskGraph(executor, "detail-page", "ads")))
                    .isInstanceOf(GraphValidationException.class)
                    .hasMessageContaining("already registered");
        } finally {
            executor.shutdownNow();
        }
    }
    private TaskGraph<Object> taskGraph(ExecutorService executor, String graphId, String taskId) {
        return TaskGraph.<Object>builder()
                .graphId(graphId)
                .executor(executor)
                .addTask(TaskDefinition.<Object>builder()
                        .taskId(taskId)
                        .handler(context -> {
                        })
                        .build())
                .build();
    }
}
