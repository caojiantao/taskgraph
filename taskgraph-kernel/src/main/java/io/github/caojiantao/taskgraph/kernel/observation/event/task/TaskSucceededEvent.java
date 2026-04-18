package io.github.caojiantao.taskgraph.kernel.observation.event.task;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * 任务成功事件。
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class TaskSucceededEvent extends TaskObservationEvent {

    private final Duration duration;

    public TaskSucceededEvent(String executionId,
                              String graphId,
                              Instant occurredAt,
                              String taskId,
                              Duration duration) {
        super(executionId, graphId, occurredAt, taskId);
        this.duration = duration;
    }
}
