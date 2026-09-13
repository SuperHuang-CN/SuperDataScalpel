package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "任务当前保存的在线 Java 源码及其与已编译 JAR 的一致性。")

public record SparkJarOnlineSourceResponse(
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "当前生产任务定义版本；完全未创建定义时为 0。仅保存在线源码不会递增该版本，编译成功并替换当前 JAR 时递增。")
        int definitionVersion,
        @Schema(description = "当前完整 Java 源码；未保存过源码时返回与任务批流模式匹配的默认模板。")
        String sourceCode,
        @Schema(description = "sourceCode UTF-8 字节的 SHA-256 十六进制摘要。")
        String sourceSha256,
        @Schema(description = "生成当前在线编译 JAR 时使用的源码摘要；当前 JAR 来自手工上传或尚未在线编译时为空。")
        String compiledSourceSha256,
        @Schema(description = "sourceCode 是否来自已持久化草稿；false 表示返回的是尚未保存的默认模板。")
        boolean persisted,
        @Schema(description = "sourceSha256 是否不同于 compiledSourceSha256；未在线编译、当前 JAR 为手工上传或仅返回默认模板时也为 true。")
        boolean hasUncompiledChanges,
        @Schema(description = "当前 JAR 来源；没有 JAR 时为空。")
        JarOrigin currentJarOrigin,
        @Schema(description = "当前任务 JAR 元数据；未上传或编译时为空。")
        SparkJarTaskDefinitionResponse.Jar currentJar
) {
    @Schema(description = "当前任务 JAR 的来源。")
    public enum JarOrigin {
        UPLOADED,
        ONLINE_COMPILED
    }
}
