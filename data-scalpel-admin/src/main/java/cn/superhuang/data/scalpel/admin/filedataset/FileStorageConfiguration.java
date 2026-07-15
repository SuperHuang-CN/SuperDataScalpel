package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.S3FileObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "data-scalpel.file-storage.s3", name = "endpoint")
public class FileStorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client fileDatasetS3Client(S3FileStorageProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(requireText(properties.endpoint(), "S3 endpoint")))
                .region(Region.of(defaultIfBlank(properties.region(), "us-east-1")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        requireText(properties.accessKey(), "S3 AccessKey"),
                        requireText(properties.secretKey(), "S3 SecretKey")
                )))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build())
                .build();
    }

    @Bean
    FileObjectStorage fileObjectStorage(S3Client fileDatasetS3Client, S3FileStorageProperties properties) {
        return new S3FileObjectStorage(
                fileDatasetS3Client,
                requireText(properties.bucket(), "S3 Bucket"),
                properties.rootPrefix()
        );
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " 尚未配置");
        }
        return value.trim();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
