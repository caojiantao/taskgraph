package io.github.caojiantao.taskgraph.spring.fixture.onerror;

import lombok.Getter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class OnErrorGraphContext {

    private final List<String> executed = new CopyOnWriteArrayList<>();
    private final List<String> errorMessages = new CopyOnWriteArrayList<>();
}
