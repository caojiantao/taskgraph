package io.github.caojiantao.taskgraph.kernel.observation;

import io.github.caojiantao.taskgraph.kernel.observation.event.GraphObservationEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskObservationEvent;
import io.github.caojiantao.taskgraph.kernel.observation.event.task.TaskStartedEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class GraphObservationDispatcherTest {

    @Test
    void shouldDispatchToParentHandlersInRegistrationOrder() {
        List<String> received = new CopyOnWriteArrayList<>();
        GraphObservationDispatcher dispatcher = new GraphObservationDispatcher(Arrays.asList(
                handler(TaskObservationEvent.class, event -> received.add("task-base")),
                handler(GraphObservationEvent.class, event -> received.add("graph-base")),
                handler(TaskStartedEvent.class, event -> received.add("task-started"))
        ));

        dispatcher.dispatch(new TaskStartedEvent("exec-1", "detail-page", Instant.now(), "product", Duration.ZERO));

        assertThat(received).containsExactly("task-base", "graph-base", "task-started");
    }

    @Test
    void shouldIgnoreHandlerFailureAndContinueDispatching() {
        List<String> received = new CopyOnWriteArrayList<>();
        GraphObservationDispatcher dispatcher = new GraphObservationDispatcher(Arrays.asList(
                handler(TaskStartedEvent.class, event -> {
                    throw new IllegalStateException("boom");
                }),
                handler(TaskStartedEvent.class, event -> received.add(event.getTaskId()))
        ));

        dispatcher.dispatch(new TaskStartedEvent("exec-1", "detail-page", Instant.now(), "product", Duration.ZERO));

        assertThat(received).containsExactly("product");
    }

    private <E extends GraphObservationEvent> GraphObservationHandler<E> handler(Class<E> eventType,
                                                                                 java.util.function.Consumer<E> consumer) {
        return new GraphObservationHandler<E>() {
            @Override
            public Class<E> eventType() {
                return eventType;
            }

            @Override
            public void handle(E event) {
                consumer.accept(event);
            }
        };
    }
}
