package cn.superhuang.data.scalpel.contract.execution;

import java.net.URI;
import java.util.UUID;

public record QualitySampleArtifactUpload(
        UUID ruleId,
        URI putUrl,
        String objectKey,
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
