package io.github.caojiantao.taskgraph.kernel.observation.event.graph;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * 图超时事件。
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class GraphTimedOutEvent extends GraphLifecycleEvent {

    private final Duration timeout;

    public GraphTimedOutEvent(String executionId, String graphId, Instant occurredAt, Duration timeout) {
        super(executionId, graphId, occurredAt);
        this.timeout = timeout;
    }
}
