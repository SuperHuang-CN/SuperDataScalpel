package cn.superhuang.data.scalpel.contract.execution;

public record ExecutionArtifactLocation(
        String manifestKey,
        String manifestSha256,
        String resultKey,
        String logKey
) {
    public ExecutionArtifactLocation {
        manifestKey = ExecutionContractValidation.objectKey(manifestKey);
        manifestSha256 = ExecutionContractValidation.sha256(manifestSha256);
        resultKey = ExecutionContractValidation.objectKey(resultKey);
        logKey = ExecutionContractValidation.objectKey(logKey);
    }
}
