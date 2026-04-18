package io.github.caojiantao.taskgraph.kernel.execution;

import io.github.caojiantao.taskgraph.kernel.exception.TaskTimeoutException;
import io.github.caojiantao.taskgraph.kernel.graph.TaskGraph;
import io.github.caojiantao.taskgraph.kernel.graph.TaskNode;
import io.github.caojiantao.taskgraph.kernel.observation.GraphObservationDispatcher;
import io.github.caojiantao.taskgraph.kernel.observation.GraphObservationHandler;
import io.github.caojiantao.taskgraph.kernel.observation.event.GraphObservationEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.graph.GraphFinishedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.graph.GraphStartedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.graph.GraphTimedOutEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskFailedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskSkippedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskStartedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskSubmissionFailedEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskSucceededEvent;
import io.github.caojiantao.taskgraph.kernel.result.GraphExecutionResult;
import io.github.caojiantao.taskgraph.kernel.result.GraphRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultGraphExecutorObservationTest {

    @Test
    void shouldPublishGraphLifecycleEventsWithStableExecutionIdPerRun() {
        java.util.concurrent.ExecutorService graphExecutor = Executors.newFixedThreadPool(2);
        try {
            TaskGraph<Map<String, Object>> graph = graphWithExecutor(graphExecutor, 1000L,
                    TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .handler(context -> context.put("product", Boolean.TRUE))
                            .build());
            List<GraphObservationEvent> events = new ArrayList<>();
            DefaultGraphExecutor executor = new DefaultGraphExecutor(dispatcher(events));

            GraphExecutionResult first = executor.execute(GraphExecutionRequest.<Map<String, Object>>builder()
                    .graph(graph)
                    .context(new java.util.HashMap<>())
                    .build());
            GraphExecutionResult second = executor.execute(GraphExecutionRequest.<Map<String, Object>>builder()
                    .graph(graph)
                    .context(new java.util.HashMap<>())
                    .build());

            assertThat(first.getState()).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(second.getState()).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(events).hasSize(8);
            assertThat(events.get(0)).isInstanceOf(GraphStartedEvent.class);
            assertThat(events.get(3)).isInstanceOf(GraphFinishedEvent.class);
            assertThat(events.get(4)).isInstanceOf(GraphStartedEvent.class);
            assertThat(events.get(7)).isInstanceOf(GraphFinishedEvent.class);
            assertThat(events.subList(0, 4))
                    .extracting(GraphObservationEvent::getExecutionId)
                    .containsOnly(events.get(0).getExecutionId());
            assertThat(events.subList(4, 8))
                    .extracting(GraphObservationEvent::getExecutionId)
                    .containsOnly(events.get(4).getExecutionId());
            assertThat(events.get(0).getExecutionId()).isNotEqualTo(events.get(4).getExecutionId());
        } finally {
            graphExecutor.shutdownNow();
        }
    }

    @Test
    void shouldPublishTimeoutAsTaskFailedWithTaskTimeoutException() {
        java.util.concurrent.ExecutorService graphExecutor = Executors.newFixedThreadPool(2);
        try {
            TaskGraph<Map<String, Object>> graph = graphWithExecutor(graphExecutor, 1000L,
                    TaskNode.<Map<String, Object>>builder()
                            .taskId("slow")
                            .timeoutMillis(50L)
                            .handler(context -> Thread.sleep(300L))
                            .build());
            List<GraphObservationEvent> events = new ArrayList<>();
            DefaultGraphExecutor executor = new DefaultGraphExecutor(dispatcher(events));

            GraphExecutionResult result = executor.execute(GraphExecutionRequest.<Map<String, Object>>builder()
                    .graph(graph)
                    .context(new java.util.HashMap<>())
                    .build());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.DEGRADED);
            assertThat(events).hasAtLeastOneElementOfType(TaskStartedEvent.class);
            assertThat(events).hasAtLeastOneElementOfType(TaskFailedEvent.class);
            TaskStartedEvent startedEvent = findEvent(events, TaskStartedEvent.class);
            assertThat(startedEvent.getQueueDuration()).isNotNull();
            TaskFailedEvent failedEvent = findEvent(events, TaskFailedEvent.class);
            assertThat(failedEvent.getTaskId()).isEqualTo("slow");
            assertThat(failedEvent.getCause()).isInstanceOf(TaskTimeoutException.class);
        } finally {
            graphExecutor.shutdownNow();
        }
    }

    @Test
    void shouldPublishSubmissionFailureAndDownstreamSkipWithoutTaskFailedEvent() {
        java.util.concurrent.ExecutorService graphExecutor = Executors.newFixedThreadPool(2);
        java.util.concurrent.ExecutorService rejectedExecutor = Executors.newSingleThreadExecutor();
        rejectedExecutor.shutdownNow();
        try {
            TaskGraph<Map<String, Object>> graph = graphWithExecutor(graphExecutor, 1000L,
                    TaskNode.<Map<String, Object>>builder()
                            .taskId("product")
                            .executor(rejectedExecutor)
                            .handler(context -> {
                            })
                            .build(),
                    TaskNode.<Map<String, Object>>builder()
                            .taskId("promotion")
                            .dependsOn("product")
                            .handler(context -> {
                            })
                            .build());
            List<GraphObservationEvent> events = new ArrayList<>();
            DefaultGraphExecutor executor = new DefaultGraphExecutor(dispatcher(events));

            GraphExecutionResult result = executor.execute(GraphExecutionRequest.<Map<String, Object>>builder()
                    .graph(graph)
                    .context(new java.util.HashMap<>())
                    .build());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.DEGRADED);
            TaskSubmissionFailedEvent submissionFailedEvent = findEvent(events, TaskSubmissionFailedEvent.class);
            assertThat(submissionFailedEvent.getTaskId()).isEqualTo("product");
            assertThat(submissionFailedEvent.getCause()).isInstanceOf(RejectedExecutionException.class);
            TaskSkippedEvent skippedEvent = findEvent(events, TaskSkippedEvent.class);
            assertThat(skippedEvent.getTaskId()).isEqualTo("promotion");
            assertThat(skippedEvent.getTriggeredByTaskId()).isEqualTo("product");
            assertThat(events).noneMatch(TaskFailedEvent.class::isInstance);
        } finally {
            graphExecutor.shutdownNow();
        }
    }

    @Test
    void shouldPublishTimedOutAndFinishedEventsWhenGraphTimesOut() {
        java.util.concurrent.ExecutorService graphExecutor = Executors.newFixedThreadPool(2);
        try {
            TaskGraph<Map<String, Object>> graph = graphWithExecutor(graphExecutor, 50L,
                    TaskNode.<Map<String, Object>>builder()
                            .taskId("slow")
                            .timeoutMillis(500L)
                            .handler(context -> Thread.sleep(300L))
                            .build());
            List<GraphObservationEvent> events = new ArrayList<>();
            DefaultGraphExecutor executor = new DefaultGraphExecutor(dispatcher(events));

            GraphExecutionResult result = executor.execute(GraphExecutionRequest.<Map<String, Object>>builder()
                    .graph(graph)
                    .context(new java.util.HashMap<>())
                    .build());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.TIMED_OUT);
            assertThat(events).hasAtLeastOneElementOfType(GraphTimedOutEvent.class);
            GraphFinishedEvent finishedEvent = findEvent(events, GraphFinishedEvent.class);
            assertThat(finishedEvent.getState()).isEqualTo(GraphRuntimeState.TIMED_OUT);
        } finally {
            graphExecutor.shutdownNow();
        }
    }

    @Test
    void shouldMeasureTaskDurationFromActualExecutionStart() {
        java.util.concurrent.ExecutorService graphExecutor = Executors.newSingleThreadExecutor();
        try {
            TaskGraph<Map<String, Object>> graph = graphWithExecutor(graphExecutor, 1000L,
                    TaskNode.<Map<String, Object>>builder()
                            .taskId("slow")
                            .handler(context -> Thread.sleep(150L))
                            .build(),
                    TaskNode.<Map<String, Object>>builder()
                            .taskId("fast")
                            .handler(context -> {
                            })
                            .build());
            List<GraphObservationEvent> events = new ArrayList<>();
            DefaultGraphExecutor executor = new DefaultGraphExecutor(dispatcher(events));

            GraphExecutionResult result = executor.execute(GraphExecutionRequest.<Map<String, Object>>builder()
                    .graph(graph)
                    .context(new java.util.HashMap<>())
                    .build());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.SUCCESS);
            List<TaskSucceededEvent> succeededEvents = new ArrayList<>();
            for (GraphObservationEvent event : events) {
                if (event instanceof TaskSucceededEvent) {
                    succeededEvents.add((TaskSucceededEvent) event);
                }
            }
            succeededEvents.sort(Comparator.comparing(TaskSucceededEvent::getTaskId));
            TaskSucceededEvent fastEvent = succeededEvents.stream()
                    .filter(event -> "fast".equals(event.getTaskId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing fast task success event"));
            assertThat(fastEvent.getDuration().toMillis()).isLessThan(100L);
        } finally {
            graphExecutor.shutdownNow();
        }
    }

    @Test
    void shouldNotTreatQueueWaitAsTaskTimeout() {
        java.util.concurrent.ExecutorService graphExecutor = Executors.newSingleThreadExecutor();
        try {
            TaskGraph<Map<String, Object>> graph = graphWithExecutor(graphExecutor, 1000L,
                    TaskNode.<Map<String, Object>>builder()
                            .taskId("slow")
                            .timeoutMillis(500L)
                            .handler(context -> Thread.sleep(150L))
                            .build(),
                    TaskNode.<Map<String, Object>>builder()
                            .taskId("queued-fast")
                            .timeoutMillis(50L)
                            .handler(context -> context.put("queued-fast", Boolean.TRUE))
                            .build());
            List<GraphObservationEvent> events = new ArrayList<>();
            DefaultGraphExecutor executor = new DefaultGraphExecutor(dispatcher(events));

            GraphExecutionResult result = executor.execute(GraphExecutionRequest.<Map<String, Object>>builder()
                    .graph(graph)
                    .context(new java.util.HashMap<>())
                    .build());

            assertThat(result.getState()).isEqualTo(GraphRuntimeState.SUCCESS);
            assertThat(events).noneMatch(TaskFailedEvent.class::isInstance);
            TaskStartedEvent queuedFastStartedEvent = events.stream()
                    .filter(TaskStartedEvent.class::isInstance)
                    .map(TaskStartedEvent.class::cast)
                    .filter(event -> "queued-fast".equals(event.getTaskId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing queued-fast started event"));
            assertThat(queuedFastStartedEvent.getQueueDuration().toMillis()).isGreaterThanOrEqualTo(100L);
            TaskSucceededEvent fastEvent = events.stream()
                    .filter(TaskSucceededEvent.class::isInstance)
                    .map(TaskSucceededEvent.class::cast)
                    .filter(event -> "queued-fast".equals(event.getTaskId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing queued-fast success event"));
            assertThat(fastEvent.getDuration().toMillis()).isLessThan(100L);
        } finally {
            graphExecutor.shutdownNow();
        }
    }

    private GraphObservationDispatcher dispatcher(List<GraphObservationEvent> events) {
        return new GraphObservationDispatcher(java.util.Collections.singletonList(new GraphObservationHandler<GraphObservationEvent>() {
            @Override
            public Class<GraphObservationEvent> eventType() {
                return GraphObservationEvent.class;
            }

            @Override
            public void handle(GraphObservationEvent event) {
                events.add(event);
            }
        }));
    }

    @SafeVarargs
    private final TaskGraph<Map<String, Object>> graphWithExecutor(java.util.concurrent.ExecutorService executor,
                                                                   long timeoutMillis,
                                                                   TaskNode<Map<String, Object>>... taskNodes) {
        TaskGraph.TaskGraphBuilder<Map<String, Object>> builder = TaskGraph.<Map<String, Object>>builder()
                .graphId("detail-page")
                .executor(executor)
                .timeoutMillis(timeoutMillis);
        for (TaskNode<Map<String, Object>> taskNode : taskNodes) {
            builder.addTask(taskNode);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private <E extends GraphObservationEvent> E findEvent(List<GraphObservationEvent> events, Class<E> eventType) {
        return (E) events.stream()
                .filter(eventType::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing event: " + eventType.getSimpleName()));
    }
}
