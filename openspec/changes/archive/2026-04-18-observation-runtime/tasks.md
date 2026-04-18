## 1. 事件模型与 SPI

- [x] 1.1 新增 `observation` 与 `observation.event` 包结构。
- [x] 1.2 定义根事件 `GraphObservationEvent`。
- [x] 1.3 定义图级事件基类 `GraphLifecycleEvent` 与任务级事件基类 `TaskObservationEvent`。
- [x] 1.4 定义图级事件：`GraphStartedEvent`、`GraphFinishedEvent`、`GraphTimedOutEvent`。
- [x] 1.5 定义任务级事件：`TaskStartedEvent`、`TaskSucceededEvent`、`TaskFailedEvent`、`TaskSkippedEvent`、`TaskSubmissionFailedEvent`。
- [x] 1.6 定义框架超时异常类型 `TaskTimeoutException`，用于通过 `TaskFailedEvent` 区分超时失败。
- [x] 1.7 定义泛型处理器接口 `GraphObservationHandler<E extends GraphObservationEvent>`。
- [x] 1.8 定义 `GraphObservationDispatcher` 与 `NoOpGraphObservationDispatcher`。
- [x] 1.9 明确并实现分发规则：同步分发、按注册顺序执行、支持父类事件类型订阅。

## 2. 执行链路接入

- [x] 2.1 为 `DefaultGraphExecutor` 增加观测分发器构造入口，默认使用 no-op 分发器。
- [x] 2.2 在图执行开始时发布 `GraphStartedEvent`。
- [x] 2.3 在图执行结束时发布 `GraphFinishedEvent`。
- [x] 2.4 在图超时时发布 `GraphTimedOutEvent`。
- [x] 2.5 保证图超时后仍然发布 `GraphFinishedEvent(state = TIMED_OUT)`。
- [x] 2.6 在任务开始执行时发布 `TaskStartedEvent`。
- [x] 2.7 在任务成功时发布 `TaskSucceededEvent`。
- [x] 2.8 在任务失败时发布 `TaskFailedEvent`。
- [x] 2.9 在任务超时时通过 `TaskFailedEvent` + `TaskTimeoutException` 表达失败原因。
- [x] 2.10 在任务提交失败时发布 `TaskSubmissionFailedEvent`，并保留底层原始提交失败原因。
- [x] 2.11 在任务被跳过时发布 `TaskSkippedEvent`。

## 3. 运行时字段与边界

- [x] 3.1 在每次图执行开始时生成唯一 `executionId`。
- [x] 3.2 对外事件字段统一使用 `Instant` 与 `Duration`。
- [x] 3.3 内部耗时计算继续使用高精度计时，再转换为 `Duration`。
- [x] 3.4 保证观测处理器异常不影响 DAG 主执行流程。
- [x] 3.5 保证单个处理器失败不影响其他处理器接收事件。
- [x] 3.6 保持 `GraphExecutionResult` 结构不因观测能力而膨胀。

## 4. 测试与文档

- [x] 4.1 补充图开始、图结束、图超时事件的单元测试。
- [x] 4.2 补充任务成功、失败、超时、跳过、提交失败事件的单元测试。
- [x] 4.3 补充任务超时通过 `TaskFailedEvent` 与 `TaskTimeoutException` 表达的单元测试。
- [x] 4.4 补充 `executionId` 唯一性与同次执行一致性测试。
- [x] 4.5 补充父类事件订阅与处理器注册顺序测试。
- [x] 4.6 补充观测处理器异常隔离测试。
- [x] 4.7 在 README 中补充最小可观测接入示例。
