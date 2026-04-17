package io.github.caojiantao.taskgraph.spring.fixture.defaultscan;

import io.github.caojiantao.taskgraph.spring.annotation.TaskGraphDefinition;
import io.github.caojiantao.taskgraph.spring.api.GraphTask;

@TaskGraphDefinition(graphId = "default-scan-graph", executor = "defaultScanExecutor")
public interface DefaultScanGraph extends GraphTask<DefaultScanContext> {
}
