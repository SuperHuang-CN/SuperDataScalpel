package cn.superhuang.data.scalpel.admin.filedataset;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime-only connection settings for the system's private file-dataset bucket. */
@ConfigurationProperties(prefix = "data-scalpel.file-storage.s3")
public record S3FileStorageProperties(
        String endpoint,
        String region,
        String bucket,
        String rootPrefix,
        String accessKey,
        String secretKey,
        boolean pathStyleAccess
) {
}
