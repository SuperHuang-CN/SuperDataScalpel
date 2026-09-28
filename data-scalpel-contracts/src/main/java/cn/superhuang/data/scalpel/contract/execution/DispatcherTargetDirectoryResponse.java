package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.UUID;

@JsonClassDescription("Dispatcher 可注册执行目标目录；不包含凭据、客户端文件路径或原始 Spark 配置")
public record DispatcherTargetDirectoryResponse(
        @JsonPropertyDescription("Dispatcher 持久化实例身份，不随地址别名改变") String dispatcherInstanceId,
        @JsonPropertyDescription("控制面协议版本；2 为旧引擎独占通道，3 为实例共享通道") int controlPlaneVersion,
        @JsonPropertyDescription("部署配置实际生效的实例消息通道；版本 3 必填，版本 2 为空") Messaging messaging,
        @JsonPropertyDescription("当前部署配置启用的执行目标；禁用目标不返回") List<Target> targets) {
    public DispatcherTargetDirectoryResponse(String dispatcherInstanceId, int controlPlaneVersion, List<Target> targets) {
        this(dispatcherInstanceId, controlPlaneVersion, null, targets);
    }
    @JsonClassDescription("同一 Dispatcher 的所有计算引擎共用的只读 Kafka 通道")
    public record Messaging(
            @JsonPropertyDescription("Admin 向此 Dispatcher 发送执行命令的 Topic") String commandTopic,
            @JsonPropertyDescription("Runner 向此 Dispatcher 上报运行事件的 Topic") String runnerEventTopic,
            @JsonPropertyDescription("Dispatcher 向 Admin 上报权威状态的 Topic；同平台通常共享") String adminEventTopic,
            @JsonPropertyDescription("实时 Runner 控制 Topic，由 Runner 事件 Topic 加 .control 得到") String runnerControlTopic) { }
    @JsonClassDescription("一个独立注册、排队和监管的执行目标")
    public record Target(
            @JsonPropertyDescription("部署侧稳定目标键，注册时原样回传") String targetKey,
            @JsonPropertyDescription("部署侧提供的显示名称") String name,
            @JsonPropertyDescription("计算后端类型") ExecutionBackendType backendType,
            @JsonPropertyDescription("物理环境身份摘要，注册时原样回传以拒绝过期发现结果") String targetFingerprint,
            @JsonPropertyDescription("执行目标最近就绪检查是否通过；不代表任务执行成功") boolean ready,
            @JsonPropertyDescription("安全的未就绪原因；就绪时为空数组") List<String> issues,
            @JsonPropertyDescription("远端当前占用此目标的引擎 UUID；未占用时为空") UUID registeredEngineId,
            @JsonPropertyDescription("远端注册状态，未占用为 UNREGISTERED") String registrationState,
            @JsonPropertyDescription("目标执行能力") Capabilities capabilities,
            @JsonPropertyDescription("Dispatcher 部署配置的任务默认资源与单次任务上限；Admin 只读，任务可自定义但不能超过上限；不是集群实时可用容量") SparkExecutionResourcePolicy resourcePolicy,
            @JsonPropertyDescription("是否正在异步检查就绪状态；true 时 ready 为 false，但不表示检查失败，可稍后重新查询；旧版本未提供时默认 false") boolean checking) {
        public Target(String targetKey, String name, ExecutionBackendType backendType, String targetFingerprint,
                      boolean ready, List<String> issues, UUID registeredEngineId, String registrationState,
                      Capabilities capabilities, SparkExecutionResourcePolicy resourcePolicy) {
            this(targetKey, name, backendType, targetFingerprint, ready, issues, registeredEngineId,
                    registrationState, capabilities, resourcePolicy, false);
        }
        public Target(String targetKey, String name, ExecutionBackendType backendType, String targetFingerprint,
                      boolean ready, List<String> issues, UUID registeredEngineId, String registrationState,
                      Capabilities capabilities) {
            this(targetKey, name, backendType, targetFingerprint, ready, issues, registeredEngineId,
                    registrationState, capabilities, null);
        }
    }
    @JsonClassDescription("目标可用执行能力")
    public record Capabilities(
            @JsonPropertyDescription("是否支持取消") boolean cancellation,
            @JsonPropertyDescription("是否支持日志收集") boolean logCollection,
            @JsonPropertyDescription("是否支持重启恢复监管") boolean restartReconciliation,
            @JsonPropertyDescription("是否已配置流式执行") boolean streaming,
            @JsonPropertyDescription("是否已配置持久化 Checkpoint") boolean durableCheckpoint) { }
}
