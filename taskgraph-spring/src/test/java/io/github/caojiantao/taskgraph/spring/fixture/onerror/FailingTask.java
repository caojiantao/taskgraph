package io.github.caojiantao.taskgraph.spring.fixture.onerror;

import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;
import org.springframework.stereotype.Component;

@Component
@TaskNodeDefinition(taskId = "failing")
public class FailingTask implements OnErrorGraph {

    @Override
    public void handle(OnErrorGraphContext context) {
        context.getExecuted().add("failing");
        throw new IllegalStateException("boom");
    }

    @Override
    public void onError(OnErrorGraphContext context, Throwable cause) {
        context.getErrorMessages().add(cause.getMessage());
    }
}
