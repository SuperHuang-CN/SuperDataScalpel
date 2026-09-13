package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

@Schema(description = "整体保存 DRAFT 或 DISABLED Spark JAR 任务的运行参数、受控资源绑定和计算资源；保留当前 JAR 与在线源码，用户程序制品通过独立上传或在线编译维护。")
public record UpdateSparkJarTaskDefinitionRequest(
        @Schema(description = "传给用户作业的普通参数，最多 100 项；不得用于传递平台凭据") @NotNull @Size(max = 100) List<@NotNull @Valid Entry> parameters,
        @Schema(description = "任务级 Spark 配置，最多 100 项；键必须以 spark. 开头，平台控制项被拒绝，值不能为空且最长 2000 个字符。旧版资源键会转换到 executionResources，不能与 executionResources 同时提交。") @NotNull @Size(max = 100) List<@NotNull @Valid Entry> sparkConf,
        @Schema(description = "用户作业可通过 SDK 访问的资源白名单，最多 200 项；发布和每次运行重新校验权限与资源状态") @NotNull @Size(max = 200) List<@NotNull @Valid ResourceBinding> resourceBindings,
        @Schema(description = "可选驱动与执行器资源规格；为空时优先保留已有定义的规格，首次配置时使用绑定计算引擎默认值。解析后的规格不能超过当前计算引擎单任务上限。") @Valid ExecutionResources executionResources,
        @Schema(description = "作业运行超时，单位秒，范围 1 到 86400；请求必须显式给出，Java 基本类型缺省会按 0 处理并校验失败。") @Min(1) @Max(86400) int timeoutSeconds
) {
    @Schema(description = "名称和值组成的任务参数或 Spark 配置")
    public record Entry(
            @Schema(description = "参数或配置名称，去除首尾空白后在同一列表内区分大小写唯一。普通参数名最长 100 个字符且不能含 CR、LF、NUL；Spark 配置还受平台键白名单约束。") @NotBlank @Size(max = 500) String name,
            @Schema(description = "参数或配置值。普通参数允许空字符串且最长 4000 个字符；Spark 配置不能为空且实际最长 2000 个字符。不得用于传递平台凭据。") @NotNull @Size(max = 4000) String value
    ) {}

    @Schema(description = "SDK 资源绑定；用户代码只能通过绑定名称及声明的访问模式访问指定资源")
    public record ResourceBinding(
            @Schema(description = "用户代码中的稳定绑定名称，在任务内唯一") @NotBlank @Size(max = 100) String bindingName,
            @Schema(description = "绑定资源类型，决定 SDK 读取或写入接口") @NotNull SparkJarResourceType resourceType,
            @Schema(description = "数据源、模型、文件数据集或 API 资源等业务资源 UUID") @NotNull UUID resourceId,
            @Schema(description = "Topic 类型绑定使用的 Topic 名称；其他资源类型为空") @Size(max = 249) String topicName,
            @Schema(description = "READ 或 WRITE 访问模式；必须符合资源类型和任务执行模式的安全边界") @NotNull SparkJarResourceAccessMode accessMode
    ) {
        public ResourceBinding(String bindingName, SparkJarResourceType resourceType, UUID resourceId,
                               SparkJarResourceAccessMode accessMode) {
            this(bindingName, resourceType, resourceId, null, accessMode);
        }
    }

    @Schema(description = "Spark 驱动与执行器资源规格")
    public record ExecutionResources(
            @Schema(description = "Spark Driver CPU 核数")
            @NotNull(message = "驱动 CPU 不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_CORES, message = "驱动 CPU 不能小于 1")
            @Max(value = SparkExecutionResourceSpec.MAX_CORES, message = "驱动 CPU 不能超过 256")
            Integer driverCores,
            @Schema(description = "Spark Driver 内存，单位 MiB")
            @NotNull(message = "驱动内存不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_MEMORY_MIB, message = "驱动内存不能小于 1024 MiB")
            @Max(value = SparkExecutionResourceSpec.MAX_MEMORY_MIB, message = "驱动内存超过平台允许范围")
            Integer driverMemoryMiB,
            @Schema(description = "Spark Executor 实例数量")
            @NotNull(message = "执行器数量不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_EXECUTORS, message = "执行器数量不能小于 1")
            @Max(value = SparkExecutionResourceSpec.MAX_EXECUTORS, message = "执行器数量不能超过 10000")
            Integer executorInstances,
            @Schema(description = "每个 Spark Executor 的 CPU 核数")
            @NotNull(message = "单执行器 CPU 不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_CORES, message = "单执行器 CPU 不能小于 1")
            @Max(value = SparkExecutionResourceSpec.MAX_CORES, message = "单执行器 CPU 不能超过 256")
            Integer executorCores,
            @Schema(description = "每个 Spark Executor 的内存，单位 MiB")
            @NotNull(message = "单执行器内存不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_MEMORY_MIB, message = "单执行器内存不能小于 1024 MiB")
            @Max(value = SparkExecutionResourceSpec.MAX_MEMORY_MIB, message = "单执行器内存超过平台允许范围")
            Integer executorMemoryMiB
    ) {
        public SparkExecutionResourceSpec toSpec() {
            return new SparkExecutionResourceSpec(
                    driverCores, driverMemoryMiB, executorInstances, executorCores, executorMemoryMiB);
        }
    }
}
