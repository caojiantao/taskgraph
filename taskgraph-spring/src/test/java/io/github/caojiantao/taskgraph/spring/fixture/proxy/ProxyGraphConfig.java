package io.github.caojiantao.taskgraph.spring.fixture.proxy;

import io.github.caojiantao.taskgraph.spring.annotation.EnableTaskGraph;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableTaskGraph(basePackages = "io.github.caojiantao.taskgraph.spring.fixture.proxy")
public class ProxyGraphConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService proxyExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    @Bean
    public ProxyGraph proxiedProductTask() {
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setInterfaces(new Class<?>[]{ProxyGraph.class});
        proxyFactory.setTarget(new ProxiedProductTask());
        return (ProxyGraph) proxyFactory.getProxy();
    }
}
