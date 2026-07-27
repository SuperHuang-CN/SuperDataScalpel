package cn.superhuang.data.scalpel.contract.execution;

import java.net.URI;

public record LaunchArtifactUpload(URI putUrl, String objectKey) {
    public LaunchArtifactUpload {
        putUrl = ExecutionContractValidation.httpUri(putUrl, "制品上传地址");
        objectKey = ExecutionContractValidation.objectKey(objectKey);
    }
}
