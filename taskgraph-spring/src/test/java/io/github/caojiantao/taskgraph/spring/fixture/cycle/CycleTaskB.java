package io.github.caojiantao.taskgraph.spring.fixture.cycle;

import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;
import org.springframework.stereotype.Component;

@Component
@TaskNodeDefinition(taskId = "b", dependsOn = {"a"})
public class CycleTaskB implements CycleGraph {

    @Override
    public void handle(CycleGraphContext context) {
    }
}
