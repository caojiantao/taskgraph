package io.github.caojiantao.taskgraph.kernel.observation.event.graph;

import io.github.caojiantao.taskgraph.kernel.result.GraphRuntimeState;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * 图结束事件。
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class GraphFinishedEvent extends GraphLifecycleEvent {

    private final GraphRuntimeState state;
    private final Duration duration;

    public GraphFinishedEvent(String executionId,
                              String graphId,
                              Instant occurredAt,
                              GraphRuntimeState state,
                              Duration duration) {
        super(executionId, graphId, occurredAt);
        this.state = state;
        this.duration = duration;
    }
}
