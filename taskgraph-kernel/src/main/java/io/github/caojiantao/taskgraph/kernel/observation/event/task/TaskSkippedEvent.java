package io.github.caojiantao.taskgraph.kernel.observation.event.task;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * 任务跳过事件。
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class TaskSkippedEvent extends TaskObservationEvent {

    private final String triggeredByTaskId;

    public TaskSkippedEvent(String executionId,
                            String graphId,
                            Instant occurredAt,
                            String taskId,
                            String triggeredByTaskId) {
        super(executionId, graphId, occurredAt, taskId);
        this.triggeredByTaskId = triggeredByTaskId;
    }
}
