package io.github.caojiantao.taskgraph.spring.fixture.cycle;

import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;
import org.springframework.stereotype.Component;

@Component
@TaskNodeDefinition(taskId = "a", dependsOn = {"b"})
public class CycleTaskA implements CycleGraph {

    @Override
    public void handle(CycleGraphContext context) {
    }
}
