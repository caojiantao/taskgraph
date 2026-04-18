package io.github.caojiantao.taskgraph.kernel.observation.event.task;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * 任务提交失败事件。
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class TaskSubmissionFailedEvent extends TaskObservationEvent {

    private final Throwable cause;

    public TaskSubmissionFailedEvent(String executionId,
                                     String graphId,
                                     Instant occurredAt,
                                     String taskId,
                                     Throwable cause) {
        super(executionId, graphId, occurredAt, taskId);
        this.cause = cause;
    }
}
