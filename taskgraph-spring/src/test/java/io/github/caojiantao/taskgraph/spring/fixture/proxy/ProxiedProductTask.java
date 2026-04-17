package io.github.caojiantao.taskgraph.spring.fixture.proxy;

import io.github.caojiantao.taskgraph.spring.annotation.TaskNodeDefinition;

@TaskNodeDefinition(taskId = "proxied-product")
public class ProxiedProductTask implements ProxyGraph {

    @Override
    public void handle(ProxyGraphContext context) {
        context.getExecuted().add("proxied-product");
    }
}
