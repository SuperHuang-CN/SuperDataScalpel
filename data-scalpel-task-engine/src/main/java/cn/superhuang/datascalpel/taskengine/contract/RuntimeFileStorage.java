package cn.superhuang.datascalpel.taskengine.contract;



public record RuntimeFileStorage(
        String endpoint,
        String region,
        String bucket,
        boolean pathStyleAccess,
        String accessKey,
        String secretKey
) implements java.io.Serializable {
}
