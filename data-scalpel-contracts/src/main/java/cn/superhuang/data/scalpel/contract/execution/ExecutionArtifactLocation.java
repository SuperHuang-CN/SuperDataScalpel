package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
public record ExecutionArtifactLocation(
        @JsonPropertyDescription("本次尝试不可变 Manifest 的对象存储 Key。")
        String manifestKey,
        @JsonPropertyDescription("Manifest 内容的 SHA-256 十六进制摘要。")
        String manifestSha256,
        @JsonPropertyDescription("本次尝试 result.json 的固定对象存储 Key。")
        String resultKey,
        @JsonPropertyDescription("本次尝试安全日志制品的对象存储 Key。")
        String logKey
) {
    public ExecutionArtifactLocation {
        manifestKey = ExecutionContractValidation.objectKey(manifestKey);
        manifestSha256 = ExecutionContractValidation.sha256(manifestSha256);
        resultKey = ExecutionContractValidation.objectKey(resultKey);
        logKey = ExecutionContractValidation.objectKey(logKey);
    }
}
