# taskgraph

`taskgraph` 是一个面向 Java 的单机 DAG 并发任务调度框架，适合处理商品详情、聚合查询、数据装配这类存在复杂前后依赖关系的并发任务场景。

当业务链路里的并发任务越来越多、依赖关系越来越复杂时，手工编排执行顺序、失败传播和超时控制往往既繁琐又难维护；`taskgraph` 希望把这些共性问题收敛成一个稳定、可复用的调度内核。当前仓库已经完成首个可用的内核版本，后续会继续围绕易用性、扩展性和工程化能力持续演进。

## 模块说明

- `taskgraph-kernel`
  单机 DAG 调度内核，负责图定义、图校验、图注册、任务调度、失败传播、超时控制与执行结果汇总。
- `taskgraph-spring`
  纯 Spring 接入层，负责注解 DSL、启动期图编译注册和按 `context` 执行。

## 当前能力

- 不可变图定义模型：`TaskGraph`、`TaskNode`
- 定义期校验与注册期校验
- 单机进程内 DAG 依赖调度
- 同步阻塞执行入口
- 任务级超时、图级超时
- 固定失败语义：失败任务跳过后继子图，无关分支继续
- 标准运行时观测 SPI
- 极简执行结果：`GraphExecutionResult`

## 快速开始

### 基础使用

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
ExecutorService promotionExecutor = Executors.newFixedThreadPool(2);

// 1. 准备上下文。
Map<String, Object> context = new ConcurrentHashMap<>();

// 2. 定义 DAG。
TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
        .graphId("detail-page")
        .executor(executor)
        .timeoutMillis(1000L)
        .addTask(TaskNode.<Map<String, Object>>builder()
                .taskId("product")
                .handler(ctx -> ctx.put("product", "iPhone"))
                .build())
        .addTask(TaskNode.<Map<String, Object>>builder()
                .taskId("promotion")
                .dependsOn("product")
                .executor(promotionExecutor)
                .timeoutMillis(200L)
                .errorHandler((ctx, ex) -> ctx.put("promotionFallback", "default-coupon"))
                .handler(ctx -> ctx.put("promotion", "coupon"))
                .build())
        .build();

// 3. 可选：注册运行时观测处理器。
GraphObservationHandler<TaskFailedEvent> failedHandler =
        new GraphObservationHandler<TaskFailedEvent>() {
            @Override
            public Class<TaskFailedEvent> eventType() {
                return TaskFailedEvent.class;
            }

            @Override
            public void handle(TaskFailedEvent event) {
                System.out.println("task failed: " + event.getTaskId() + ", cause=" + event.getCause());
            }
        };

GraphObservationHandler<GraphFinishedEvent> finishedHandler =
        new GraphObservationHandler<GraphFinishedEvent>() {
            @Override
            public Class<GraphFinishedEvent> eventType() {
                return GraphFinishedEvent.class;
            }

            @Override
            public void handle(GraphFinishedEvent event) {
                System.out.println("graph finished: " + event.getState() + ", duration=" + event.getDuration());
            }
        };

GraphObservationDispatcher dispatcher =
        new GraphObservationDispatcher(Arrays.asList(failedHandler, finishedHandler));

// 4. 构造执行请求。
GraphExecutionRequest<Map<String, Object>> request = GraphExecutionRequest.<Map<String, Object>>builder()
        .graph(graph)
        .context(context)
        .build();

// 5. 执行图并获取结果；如果不需要观测，直接 new DefaultGraphExecutor() 即可。
GraphExecutionResult result = new DefaultGraphExecutor(dispatcher).execute(request);
```

### Spring 使用

`taskgraph-spring` 会在容器启动阶段把图接口和任务实现类编译成 `taskgraph-kernel` 的 `TaskGraph`；Spring 层只负责声明、扫描和注册，不重新定义失败传播、超时处理与执行结果语义。

```java
// 配置类
@Configuration
@EnableTaskGraph
@ComponentScan("com.example.taskgraph")
public class TaskGraphConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService detailExecutor() {
        return Executors.newFixedThreadPool(4);
    }
}

// 图接口
@TaskGraphDefinition(
        graphId = "detail-page",
        executor = "detailExecutor",
        timeoutMillis = 1000L
)
public interface DetailGraph extends GraphTask<DetailContext> {
}

// 任务节点 1
@Component
@TaskNodeDefinition(taskId = "product")
public class ProductTask implements DetailGraph {

    @Override
    public void handle(DetailContext context) {
        context.put("product", "iPhone");
    }
}

// 任务节点 2
@Component
@TaskNodeDefinition(taskId = "promotion", dependsOn = {"product"})
public class PromotionTask implements DetailGraph {

    @Override
    public void handle(DetailContext context) {
        context.put("promotion", "coupon");
    }

    @Override
    public void onError(DetailContext context, Throwable cause) {
        context.put("promotionFallback", "default-coupon");
    }
}

// 业务调用
@Component
public class DetailService {

    private final TaskGraphTemplate taskGraphTemplate;

    public DetailService(TaskGraphTemplate taskGraphTemplate) {
        this.taskGraphTemplate = taskGraphTemplate;
    }

    public GraphExecutionResult load() {
        DetailContext context = new DetailContext();
        return taskGraphTemplate.execute(context);
    }
}
```

## 后续展望

- 日志、指标与 Tracing 集成
- 线程池治理与参数动态下发
- 更丰富的测试样例和最佳实践
