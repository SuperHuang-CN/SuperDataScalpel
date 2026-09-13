package cn.superhuang.data.scalpel.business.task.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "提交 DRAFT 或 DISABLED Spark JAR 任务的单文件 Java 源码；保存接口只持久化草稿，编译和试运行接口会先保存同一份源码再继续处理。")
public record SaveSparkJarOnlineSourceRequest(
        @Schema(description = "完整 Java 源码。服务端统一 CRLF/CR 为 LF，拒绝 NUL，UTF-8 编码后不得超过 256 KiB；字符数校验也限制为 262144。") @NotBlank @Size(max = 262_144) String sourceCode
) {
}
