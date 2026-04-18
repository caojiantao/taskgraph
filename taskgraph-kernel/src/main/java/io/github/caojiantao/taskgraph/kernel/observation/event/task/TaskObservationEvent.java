package io.github.caojiantao.taskgraph.kernel.observation.event.task;

import io.github.caojiantao.taskgraph.kernel.observation.event.GraphObservationEvent;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * 任务级观测事件基类。
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class TaskObservationEvent extends GraphObservationEvent {

    private final String taskId;

    protected TaskObservationEvent(String executionId, String graphId, Instant occurredAt, String taskId) {
        super(executionId, graphId, occurredAt);
        this.taskId = taskId;
    }
}
