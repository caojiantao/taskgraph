## 背景

当前 `taskgraph-kernel` 已经完成单机 DAG 调度内核的首版实现，但在 Spring 应用中，用户仍然缺少一种既有框架感、又不破坏内核边界的声明方式。

如果仅仅让调用方继续手工声明 `TaskGraph` Bean，再在 Spring 启动期自动注册，虽然能解决接入问题，但对于一个面向 Spring 场景的框架来说，表达力仍然偏弱，难以体现“图定义”“任务节点”“依赖关系”和“任务实现”的关系。

因此，Spring 接入层需要提供一套独立的注解 DSL，使调用方能够：

- 用接口声明一张图
- 用实现类声明图中的任务节点
- 用统一接口约束任务执行与错误处理
- 在 Spring 启动阶段自动把这套 DSL 编译成内核可执行的 `TaskGraph`

同时，这套 Spring DSL 仍然必须建立在已归档的内核规范之上，不能长出第二套执行语义。

## 变更内容

- 新增 `taskgraph-spring` 方向的 OpenSpec change。
- 定义“图接口 + 任务实现类”的 Spring 注解 DSL。
- 定义统一的任务执行契约 `GraphTask<C>`。
- 规划 Spring 启动期 DSL 解析、图编译与自动注册机制。
- 保留仅通过 `context` 发起图执行的 Spring 门面能力。
- 明确采用“先扫描图接口，再按图接口从容器收集实现 Bean”的启动期装配策略。
- 当前阶段以纯 Spring 接入为主，`@EnableTaskGraph` 作为标准启用入口。

## 能力范围

### 新增能力

- `spring-integration`：提供 `taskgraph` 在 Spring 容器中的注解式图声明、启动期图编译、自动注册与按 `context` 执行能力。

### 变更能力

- 无。

## 影响范围

- 将新增 `taskgraph-spring` 模块。
- 需要引入 Spring 注解 DSL、启动期图解析器与自动注册逻辑。
- 需要补充 Spring 集成测试和 DSL 使用文档。
- 为后续 `Spring Boot Starter`、更丰富的 DSL 约束和监控能力建立基础，但不作为当前主路径。
