package io.github.caojiantao.taskgraph.spring.fixture.proxy;

import lombok.Getter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class ProxyGraphContext {

    private final List<String> executed = new CopyOnWriteArrayList<>();
}
