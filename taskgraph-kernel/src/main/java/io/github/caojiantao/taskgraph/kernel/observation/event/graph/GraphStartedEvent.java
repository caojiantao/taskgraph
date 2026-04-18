package io.github.caojiantao.taskgraph.kernel.observation.event.graph;

import java.time.Instant;

/**
 * 图开始事件。
 */
public final class GraphStartedEvent extends GraphLifecycleEvent {

    public GraphStartedEvent(String executionId, String graphId, Instant occurredAt) {
        super(executionId, graphId, occurredAt);
    }
}
