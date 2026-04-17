package io.github.caojiantao.taskgraph.spring.config;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 图扫描配置。
 */
@Getter
@ToString
@EqualsAndHashCode
public final class TaskGraphScanProperties {

    private final List<String> basePackages;

    public TaskGraphScanProperties(List<String> basePackages) {
        List<String> packages = basePackages == null ? Collections.emptyList() : basePackages;
        this.basePackages = Collections.unmodifiableList(new ArrayList<>(packages));
    }
}
