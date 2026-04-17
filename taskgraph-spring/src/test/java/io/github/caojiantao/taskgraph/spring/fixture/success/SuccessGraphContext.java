package io.github.caojiantao.taskgraph.spring.fixture.success;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class SuccessGraphContext {

    private final Map<String, Object> values = new ConcurrentHashMap<>();
    private final List<String> executed = new CopyOnWriteArrayList<>();
}
