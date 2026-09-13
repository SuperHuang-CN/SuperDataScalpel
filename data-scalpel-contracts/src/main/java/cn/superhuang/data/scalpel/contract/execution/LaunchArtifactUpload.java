package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.net.URI;

public record LaunchArtifactUpload(
        @JsonPropertyDescription("短期有效且仅允许 PUT 的预签名对象上传地址。")
        URI putUrl,
        @JsonPropertyDescription("平台指定的对象存储 Key，Runner 不得改写。")
        String objectKey
) {
    public LaunchArtifactUpload {
        putUrl = ExecutionContractValidation.httpUri(putUrl, "制品上传地址");
        objectKey = ExecutionContractValidation.objectKey(objectKey);
    }
}
