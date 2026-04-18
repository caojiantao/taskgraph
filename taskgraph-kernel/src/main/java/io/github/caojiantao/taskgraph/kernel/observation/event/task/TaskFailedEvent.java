package io.github.caojiantao.taskgraph.kernel.observation.event.task;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * 任务失败事件。
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class TaskFailedEvent extends TaskObservationEvent {

    private final Throwable cause;
    private final Duration duration;

    public TaskFailedEvent(String executionId,
                           String graphId,
                           Instant occurredAt,
                           String taskId,
                           Throwable cause,
                           Duration duration) {
        super(executionId, graphId, occurredAt, taskId);
        this.cause = cause;
        this.duration = duration;
    }
}
