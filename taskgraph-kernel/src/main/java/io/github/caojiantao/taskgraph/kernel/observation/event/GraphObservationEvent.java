package io.github.caojiantao.taskgraph.kernel.observation.event;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.time.Instant;

/**
 * 运行时观测事件根类。
 */
@Getter
@ToString
@EqualsAndHashCode
public abstract class GraphObservationEvent {

    @NonNull
    private final String executionId;

    @NonNull
    private final String graphId;

    @NonNull
    private final Instant occurredAt;

    protected GraphObservationEvent(String executionId, String graphId, Instant occurredAt) {
        this.executionId = executionId;
        this.graphId = graphId;
        this.occurredAt = occurredAt;
    }
}
