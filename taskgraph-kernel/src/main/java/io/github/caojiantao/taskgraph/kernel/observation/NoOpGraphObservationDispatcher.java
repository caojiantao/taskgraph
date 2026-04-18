package io.github.caojiantao.taskgraph.kernel.observation;

import io.github.caojiantao.taskgraph.kernel.observation.event.GraphObservationEvent;

import java.util.Collections;

/**
 * 空观测分发器。
 */
public final class NoOpGraphObservationDispatcher extends GraphObservationDispatcher {

    private static final NoOpGraphObservationDispatcher INSTANCE = new NoOpGraphObservationDispatcher();

    private NoOpGraphObservationDispatcher() {
        super(Collections.emptyList());
    }

    public static NoOpGraphObservationDispatcher getInstance() {
        return INSTANCE;
    }

    @Override
    public void dispatch(GraphObservationEvent event) {
        // no-op
    }
}
