package io.github.caojiantao.taskgraph.spring.fixture.defaultscan;

import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;
import org.springframework.stereotype.Component;

@Component
@TaskNodeDefinition(taskId = "default-scan")
public class DefaultScanTask implements DefaultScanGraph {

    @Override
    public void handle(DefaultScanContext context) {
        context.getExecuted().add("default-scan");
    }
}
