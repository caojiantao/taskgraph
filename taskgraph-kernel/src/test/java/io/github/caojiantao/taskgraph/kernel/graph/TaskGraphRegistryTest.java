package io.github.caojiantao.taskgraph.kernel.graph;

import io.github.caojiantao.taskgraph.kernel.exception.GraphValidationException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskGraphRegistryTest {

    @Test
    void shouldRejectDuplicateGraphIdInSameRegistry() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TaskGraphRegistry registry = new TaskGraphRegistry();
            registry.register(taskGraph(executor, "detail-page", "product"));

            assertThatThrownBy(() -> registry.register(taskGraph(executor, "detail-page", "ads")))
                    .isInstanceOf(GraphValidationException.class)
                    .hasMessageContaining("already registered");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldAllowSameTaskIdAcrossDifferentGraphs() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TaskGraphRegistry registry = new TaskGraphRegistry();

            // taskId 只要求在单图内唯一，跨图复用同名任务是允许的。
            registry.register(taskGraph(executor, "detail-page", "product"));
            registry.register(taskGraph(executor, "trade-page", "product"));

            assertThat(registry.contains("detail-page")).isTrue();
            assertThat(registry.contains("trade-page")).isTrue();
            assertThat(registry.getAll()).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private TaskGraph<Object> taskGraph(ExecutorService executor, String graphId, String taskId) {
        return TaskGraph.<Object>builder()
                .graphId(graphId)
                .executor(executor)
                .addTask(TaskNode.<Object>builder()
                        .taskId(taskId)
                        .handler(context -> {
                        })
                        .build())
                .build();
    }
}
