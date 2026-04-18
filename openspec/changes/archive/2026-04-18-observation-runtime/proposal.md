## 背景

当前 `taskgraph-kernel` 已经完成单机 DAG 调度内核的最小可行实现，但对于框架自身运行过程中的“发生了什么”，仍然缺少一套稳定、标准的暴露机制。

这会带来几个现实问题：

- 调用方难以感知一张图何时开始、何时结束、何时超时
- 调用方难以感知某个任务是成功、失败、超时、跳过还是提交失败
- 后续若要接日志、指标、Tracing、告警，只能在外层做不稳定的包裹或侵入式改造

因此，内核需要先提供一套标准的运行时可观测性 SPI，把图与任务执行过程中的关键语义稳定暴露出来，再在此基础上演进外围集成能力。

## 变更内容

- 新增 `observation-runtime` 方向的 OpenSpec change。
- 在 `taskgraph-kernel` 中新增标准运行时观测 SPI。
- 采用“事件模型 + 泛型处理器 + 内部分发器”的扩展方式。
- 明确图级事件与任务级事件的基础模型与首版事件集合。
- 明确 `GraphObservationDispatcher` 挂载在 `GraphExecutor` 上，而不是挂在单次执行请求上。
- 明确每次图执行由内核自动生成唯一 `executionId`。
- 明确对外事件字段统一使用 `Instant` 与 `Duration`，不混用裸 `millis` / `nanos`。
- 明确观测逻辑属于 best-effort，不能反向影响 DAG 主执行流程。

## 能力范围

### 新增能力

- `observation-runtime`：提供 `taskgraph-kernel` 的运行时观测事件、事件处理 SPI、事件分发能力与标准触发时机。

### 变更能力

- `single-node-dag-kernel`：在不改变既有调度、失败传播、超时处理和结构化结果语义的前提下，补充标准可观测扩展点。

## 影响范围

- 将在 `taskgraph-kernel` 中新增 `observation` 相关包与类型。
- 需要在 `DefaultGraphExecutor` 与 `TaskDispatcher` 中接入事件发布逻辑。
- 需要补充观测事件分发与 best-effort 约束的单元测试。
- 需要在 README 中补充一个最小可观测接入示例。
- 为后续日志集成、Micrometer 集成、Tracing 集成建立基础，但不作为本次主路径。
