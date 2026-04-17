package io.github.caojiantao.taskgraph.spring.fixture.duplicate;

import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;

@TaskGraphDefinition(graphId = "duplicate-task-graph", executor = "duplicateExecutor")
public interface DuplicateTaskGraph extends GraphTask<DuplicateTaskGraphContext> {
}
