package io.github.caojiantao.taskgraph.kernel.observation;

import io.github.caojiantao.taskgraph.kernel.observation.event.GraphObservationEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 运行时观测事件分发器。
 */
public class GraphObservationDispatcher {

    private final List<GraphObservationHandler<? extends GraphObservationEvent>> handlers;

    public GraphObservationDispatcher(List<GraphObservationHandler<? extends GraphObservationEvent>> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            this.handlers = Collections.emptyList();
        } else {
            this.handlers = Collections.unmodifiableList(new ArrayList<>(handlers));
        }
    }

    public void dispatch(GraphObservationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        for (GraphObservationHandler<? extends GraphObservationEvent> handler : handlers) {
            if (handler.eventType().isAssignableFrom(event.getClass())) {
                invoke(handler, event);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends GraphObservationEvent> void invoke(GraphObservationHandler<E> handler, GraphObservationEvent event) {
        try {
            handler.handle((E) event);
        } catch (Throwable ignored) {
            // 观测处理属于 best-effort，处理器异常不能影响主执行流程和其他处理器。
        }
    }
}
