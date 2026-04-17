## 1. 模块与 DSL 骨架

- [x] 1.1 新增 `taskgraph-spring` 模块，并引入最小 Spring 依赖。
- [x] 1.2 提供 `@EnableTaskGraph` 入口，仅支持可选 `basePackages` 参数。
- [x] 1.3 约定默认扫描启用配置类包及其子包。
- [x] 1.4 实现 `TaskGraphImportRegistrar`，用于导入框架基础设施 Bean。
- [x] 1.5 定义图级注解 `@TaskGraphDefinition`。
- [x] 1.6 定义节点级注解 `@TaskNodeDefinition`。
- [x] 1.7 定义统一任务契约 `GraphTask<C>`。

## 2. 启动期图编译

- [x] 2.1 实现图接口扫描。
- [x] 2.2 按图接口类型从 Spring 容器收集对应任务实现 Bean。
- [x] 2.3 实现 `TaskGraphRegistrar`，串起扫描、收集、编译和注册流程。
- [x] 2.4 实现从 DSL 到 `TaskDefinition` 的编译逻辑。
- [x] 2.5 实现从 DSL 到 `TaskGraph` 的编译逻辑。
- [x] 2.6 在启动期将编译得到的图注册到统一的 `TaskGraphRegistry`。
- [x] 2.7 兼容 AOP 代理 Bean，按目标类解析任务注解与图归属。

## 3. Spring 基础 Bean 与执行门面

- [x] 3.1 通过基础设施导入链路注册 `TaskGraphRegistry` Bean。
- [x] 3.2 通过基础设施导入链路注册 `GraphExecutor` Bean。
- [x] 3.3 通过基础设施导入链路注册 `ContextGraphRouter` Bean。
- [x] 3.4 定义 `TaskGraphTemplate` 或等价门面类型，并注册到容器。
- [x] 3.5 支持仅通过 `context` 发起一次图执行。
- [x] 3.6 构建 `contextType -> graphId` 路由索引，并确保上下文类型唯一绑定。
- [x] 3.7 执行门面先通过路由索引解析 `graphId`，再委托 `TaskGraphRegistry` 获取目标图。
- [x] 3.8 基础设施 Bean 采用“用户优先，框架兜底”的默认注册策略。

## 4. 启动期校验

- [x] 4.1 校验图接口元数据完整性。
- [x] 4.2 校验任务节点元数据完整性。
- [x] 4.3 校验同图 `taskId` 唯一性、依赖存在性与无环约束。
- [x] 4.4 校验线程池配置在 Spring 容器中的可解析性。
- [x] 4.5 校验任务实现类必须且只能实现一个带图定义注解的图接口。
- [x] 4.6 校验同一个上下文类型只能对应一张图。
- [x] 4.7 校验被扫描到的图接口至少存在一个任务实现 Bean。
- [x] 4.8 校验任务 Bean 必须为单例，且首版拒绝 `prototype` 与 `@Lazy`。

## 5. 测试

- [x] 5.1 补充 Spring 上下文启动测试。
- [x] 5.2 补充 DSL 编译成 `TaskGraph` 的测试。
- [x] 5.3 补充重复 `taskId`、缺失依赖、成环的启动失败测试。
- [x] 5.4 补充按 `context` 执行图的集成测试。
- [x] 5.5 补充任务失败后 `onError` 被调用的测试。

## 6. 文档

- [x] 6.1 在 README 中补充 Spring 注解 DSL 示例。
- [x] 6.2 说明 Spring DSL 最终会编译成 `taskgraph-kernel` 的 `TaskGraph`。
- [x] 6.3 说明 Spring 层不改变内核失败/超时语义。
