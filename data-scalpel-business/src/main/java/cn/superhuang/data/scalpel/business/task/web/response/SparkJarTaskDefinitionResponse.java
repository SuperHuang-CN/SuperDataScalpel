package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Spark JAR 任务的当前配置、JAR 元数据和解析后运行资源；响应可在尚无 JAR或尚未满足发布条件时返回。")

public record SparkJarTaskDefinitionResponse(
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "当前定义是否已经关联 JAR 制品元数据；true 仍不保证资源绑定、制品存储或计算引擎满足发布和运行条件。")
        boolean configured,
        @Schema(description = "当前任务定义版本；完全未配置时为 0，首次保存配置会从内部初始版本 1 递增。参数、Spark 配置、运行资源、资源绑定或 JAR 变化时递增；仅保存在线源码不递增。旧运行保留提交时版本。")
        int definitionVersion,
        @Schema(description = "任务类型要求的批处理或流式作业模式；未上传 JAR 时也会返回预期模式。")
        SparkJarJobMode jobMode,
        @Schema(description = "当前已上传或在线编译 JAR 的固定元数据；尚无 JAR 时为空。")
        Jar jar,
        @Schema(description = "传给用户作业的普通参数；不得携带平台凭据。")
        List<Entry> parameters,
        @Schema(description = "任务级 Spark 配置白名单项。")
        List<Entry> sparkConf,
        @Schema(description = "允许用户 JAR 访问的平台资源白名单。")
        List<ResourceBinding> resourceBindings,
        @Schema(description = "当前解析后的 Spark Driver 与 Executor 资源规格；即使尚未保存定义也返回计算引擎默认值，保存及发布时不得超过引擎上限。")
        SparkExecutionResourceSpec executionResources,
        @Schema(description = "任务定义保存的作业超时，单位秒；完全未配置时返回默认值 3600。批作业超时后平台请求终止，流式任务的正常生命周期由启动、停止接口管理。")
        int timeoutSeconds,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    @Schema(description = "任务固定使用的 JAR 文件及入口类元数据。")
    public record Jar(
            @Schema(description = "文件显示名称。")
            String fileName,
            @Schema(description = "文件内容的 SHA-256 十六进制摘要。")
            String sha256,
            @Schema(description = "已固定 JAR 文件大小，单位字节。")
            long sizeBytes,
            @Schema(description = "实现公开 SDK 作业接口的全限定 Java 类名。")
            String jobClass,
            @Schema(description = "JAR 声明的公开作业 API 版本。")
            int jobApiVersion,
            @Schema(description = "JAR 声明的批处理或流式作业模式。")
            SparkJarJobMode jobMode
    ) {}
    @Schema(description = "名称和值组成的任务参数或 Spark 配置项。")
    public record Entry(
            @Schema(description = "参数名或 Spark 配置键。")
            String name,
            @Schema(description = "参数值；敏感凭据不应通过此字段传递。")
            String value
    ) {}
    @Schema(description = "向用户 JAR 开放的平台资源白名单；运行时凭据由平台注入。")
    public record ResourceBinding(
            @Schema(description = "JAR 代码使用的资源绑定名，在单个任务定义内唯一。")
            String bindingName,
            @Schema(description = "绑定的平台资源类型。")
            SparkJarResourceType resourceType,
            @Schema(description = "绑定资源 UUID。")
            UUID resourceId,
            @Schema(description = "绑定资源的当前显示名称。")
            String resourceName,
            @Schema(description = "Kafka 或 TDengine TMQ 主题名；非主题资源为空。")
            String topicName,
            @Schema(description = "用户作业对该资源允许的访问方式。")
            SparkJarResourceAccessMode accessMode
    ) {}
}
