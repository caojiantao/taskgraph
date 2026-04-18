package io.github.caojiantao.taskgraph.kernel.observation.event.graph;

import io.github.caojiantao.taskgraph.kernel.observation.event.GraphObservationEvent;

import java.time.Instant;

/**
 * 图级生命周期事件基类。
 */
public abstract class GraphLifecycleEvent extends GraphObservationEvent {

    protected GraphLifecycleEvent(String executionId, String graphId, Instant occurredAt) {
        super(executionId, graphId, occurredAt);
    }
}
