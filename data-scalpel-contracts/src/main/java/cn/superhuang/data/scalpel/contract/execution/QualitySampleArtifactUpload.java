package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.net.URI;
import java.util.UUID;

public record QualitySampleArtifactUpload(
        @JsonPropertyDescription("需要上传失败样本的模型质量规则 UUID。")
        UUID ruleId,
        @JsonPropertyDescription("短期有效且仅允许 PUT 的预签名对象上传地址。")
        URI putUrl,
        @JsonPropertyDescription("平台指定的对象存储 Key，Runner 不得改写。")
        String objectKey,
        @JsonPropertyDescription("允许下载或上传的最大字节数，超过时必须拒绝。")
        int maximumBytes
) {
    public QualitySampleArtifactUpload {
        if (ruleId == null || maximumBytes < 1 || maximumBytes > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("质检样本上传信息无效");
        }
        putUrl = ExecutionContractValidation.httpUri(putUrl, "质检样本上传地址");
        objectKey = ExecutionContractValidation.objectKey(objectKey);
    }
}
