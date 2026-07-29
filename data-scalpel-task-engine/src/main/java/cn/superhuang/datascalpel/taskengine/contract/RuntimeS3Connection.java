package cn.superhuang.datascalpel.taskengine.contract;

import java.io.Serializable;

public record RuntimeS3Connection(
        String endpoint,
        String region,
        String bucket,
        String rootPrefix,
        boolean pathStyleAccess,
        String accessKey,
        String secretKey
) implements Serializable {
}
