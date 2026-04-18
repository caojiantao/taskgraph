package io.github.caojiantao.taskgraph.kernel.observation.event.task;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * 任务开始事件。
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public final class TaskStartedEvent extends TaskObservationEvent {

    // 从任务提交成功到真正开始执行之间的排队等待时间。
    private final Duration queueDuration;

    public TaskStartedEvent(String executionId,
                            String graphId,
                            Instant occurredAt,
                            String taskId,
                            Duration queueDuration) {
        super(executionId, graphId, occurredAt, taskId);
        this.queueDuration = queueDuration;
    }
}
