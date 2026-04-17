package io.github.caojiantao.taskgraph.spring.fixture.missingdep;

import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;
import org.springframework.stereotype.Component;

@Component
@TaskNodeDefinition(taskId = "promotion", dependsOn = {"product"})
public class MissingDependencyTask implements MissingDependencyGraph {

    @Override
    public void handle(MissingDependencyGraphContext context) {
    }
}
