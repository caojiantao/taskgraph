package io.github.caojiantao.taskgraph.kernel.validation;

import io.github.caojiantao.taskgraph.kernel.exception.GraphValidationException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskDefinition;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultTaskGraphDefinitionValidatorTest {

    @Test
    void shouldBuildValidGraph() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            TaskGraph<Object> graph = TaskGraph.<Object>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .addTask(task("product"))
                    .addTask(TaskDefinition.<Object>builder()
                            .taskId("promotion")
                            .dependsOn("product")
                            .handler(context -> {
                            })
                            .build())
                    .build();

            assertThat(graph.getGraphId()).isEqualTo("detail-page");
            assertThat(graph.getTasks()).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectDuplicateTaskIds() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> TaskGraph.<Object>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .addTask(task("product"))
                    .addTask(task("product"))
                    .build())
                    .isInstanceOf(GraphValidationException.class)
                    .hasMessageContaining("duplicate taskId");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectMissingDependency() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> TaskGraph.<Object>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .addTask(TaskDefinition.<Object>builder()
                            .taskId("promotion")
                            .dependsOn("product")
                            .handler(context -> {
                            })
                            .build())
                    .build())
                    .isInstanceOf(GraphValidationException.class)
                    .hasMessageContaining("missing task");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectCycle() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> TaskGraph.<Object>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .addTask(TaskDefinition.<Object>builder()
                            .taskId("a")
                            .dependsOn("b")
                            .handler(context -> {
                            })
                            .build())
                    .addTask(TaskDefinition.<Object>builder()
                            .taskId("b")
                            .dependsOn("a")
                            .handler(context -> {
                            })
                            .build())
                    .build())
                    .isInstanceOf(GraphValidationException.class)
                    .hasMessageContaining("cyclic");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectMissingEffectiveExecutor() {
        assertThatThrownBy(() -> TaskGraph.<Object>builder()
                .graphId("detail-page")
                .addTask(task("product"))
                .build())
                .isInstanceOf(GraphValidationException.class)
                .hasMessageContaining("no effective executor");
    }

    @Test
    void shouldRejectEmptyGraph() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> TaskGraph.<Object>builder()
                    .graphId("detail-page")
                    .executor(executor)
                    .build())
                    .isInstanceOf(GraphValidationException.class)
                    .hasMessageContaining("at least one task");
        } finally {
            executor.shutdownNow();
        }
    }

    private TaskDefinition<Object> task(String taskId) {
        return TaskDefinition.<Object>builder()
                .taskId(taskId)
                .handler(context -> {
                })
                .build();
    }
}
