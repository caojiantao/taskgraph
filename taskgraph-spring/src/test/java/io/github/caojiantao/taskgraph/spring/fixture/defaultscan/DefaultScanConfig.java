package io.github.caojiantao.taskgraph.spring.fixture.defaultscan;

import io.github.caojiantao.taskgraph.spring.annotation.EnableTaskGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableTaskGraph
@ComponentScan(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.defaultscan")
public class DefaultScanConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService defaultScanExecutor() {
        return Executors.newFixedThreadPool(2);
    }
}
