# taskgraph

`taskgraph` 是一个面向 Java 的单机 DAG 并发任务调度框架，适合处理商品详情、聚合查询、数据装配这类存在复杂前后依赖关系的并发任务场景。

当业务链路里的并发任务越来越多、依赖关系越来越复杂时，手工编排执行顺序、失败传播和超时控制往往既繁琐又难维护；`taskgraph` 希望把这些共性问题收敛成一个稳定、可复用的调度内核。当前仓库已经完成首个可用的内核版本，后续会继续围绕易用性、扩展性和工程化能力持续演进。

## 模块说明

- `taskgraph-kernel`
  单机 DAG 调度内核，负责图定义、图校验、图注册、任务调度、失败传播、超时控制与执行结果汇总。

## 当前能力

- 不可变图定义模型：`TaskGraph`、`TaskDefinition`
- 定义期校验与注册期校验
- 单机进程内 DAG 依赖调度
- 同步阻塞执行入口
- 任务级超时、图级超时
- 固定失败语义：失败任务跳过后继子图，无关分支继续
- 极简执行结果：`GraphExecutionResult`

## 快速开始

下面是一段从创建上下文到拿到执行结果的完整示例：

```java
ExecutorService executor = Executors.newFixedThreadPool(4);
ExecutorService promotionExecutor = Executors.newFixedThreadPool(2);

// 1. 先准备业务上下文，任务执行过程中会直接往这里写结果。
Map<String, Object> context = new ConcurrentHashMap<>();

// 2. 定义 DAG。
TaskGraph<Map<String, Object>> graph = TaskGraph.<Map<String, Object>>builder()
        .graphId("detail-page")
        .executor(executor)
        .timeoutMillis(1000L)
        .addTask(TaskDefinition.<Map<String, Object>>builder()
                .taskId("product")
                .handler(ctx -> ctx.put("product", "iPhone"))
                .build())
        .addTask(TaskDefinition.<Map<String, Object>>builder()
                .taskId("promotion")
                .dependsOn("product")
                .executor(promotionExecutor) // 可选：任务级专属线程池，会覆盖图级 executor。
                .timeoutMillis(200L) // 可选：任务级超时，会覆盖默认任务超时常量。
                .errorHandler((ctx, ex) -> ctx.put("promotionFallback", "default-coupon")) // 可选：任务失败回调。
                .handler(ctx -> ctx.put("promotion", "coupon"))
                .build())
        .build();

// 3. 可选：如果应用里会维护多张图，可以先注册，便于统一管理。
TaskGraphRegistry registry = new TaskGraphRegistry();
registry.register(graph);

// 4. 显式构造执行请求并获取结果。
GraphExecutionRequest<Map<String, Object>> request = GraphExecutionRequest.<Map<String, Object>>builder()
        .graph(graph)
        .context(context)
        .build();

// 5. 执行图并获取结果。
GraphExecutionResult result = GraphExecutors.defaultExecutor()
        .execute(request);
```

## 后续展望

- Spring 接入与自动装配
- 监控与运行时可观测性
- 线程池治理与参数动态下发
- 更丰富的测试样例和最佳实践
