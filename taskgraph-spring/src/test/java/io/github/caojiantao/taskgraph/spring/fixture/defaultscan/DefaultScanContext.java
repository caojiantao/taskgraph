package io.github.caojiantao.taskgraph.spring.fixture.defaultscan;

import lombok.Getter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class DefaultScanContext {

    private final List<String> executed = new CopyOnWriteArrayList<>();
}
