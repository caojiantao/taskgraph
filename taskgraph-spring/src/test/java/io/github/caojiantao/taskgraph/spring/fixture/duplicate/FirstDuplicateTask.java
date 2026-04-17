package io.github.caojiantao.taskgraph.spring.fixture.duplicate;

import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;
import org.springframework.stereotype.Component;

@Component
@TaskNodeDefinition(taskId = "same-task")
public class FirstDuplicateTask implements DuplicateTaskGraph {

    @Override
    public void handle(DuplicateTaskGraphContext context) {
    }
}
