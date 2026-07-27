package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactStorage;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

final class S3TaskRunArtifactStorage implements TaskRunArtifactStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String rootPrefix;

    S3TaskRunArtifactStorage(
            S3Client client,
            S3Presigner presigner,
            String bucket,
            String rootPrefix
    ) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.rootPrefix = normalizePrefix(rootPrefix);
    }

    @Override
    public void store(String objectKey, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(resolve(objectKey))
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(resolve(objectKey))
                .build());
    }

    @Override
    public URI presignGet(String objectKey, Duration lifetime) {
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(resolve(objectKey)).build();
        return URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(lifetime)
                        .getObjectRequest(request)
                        .build())
                .url().toString());
    }

    @Override
    public URI presignPut(String objectKey, String contentType, Duration lifetime) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket).key(resolve(objectKey)).contentType(contentType).build();
        return URI.create(presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(lifetime)
                        .putObjectRequest(request)
                        .build())
                .url().toString());
    }

    @Override
    public Optional<byte[]> readIfPresent(String objectKey, int maximumBytes) {
        try (var response = client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(resolve(objectKey)).build())) {
            long contentLength = response.response().contentLength();
            if (contentLength > maximumBytes) {
                throw new IllegalStateException("任务运行制品超过允许大小");
            }
            byte[] content = response.readAllBytes();
            if (content.length > maximumBytes) {
                throw new IllegalStateException("任务运行制品超过允许大小");
            }
            return Optional.of(content);
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return Optional.empty();
            throw exception;
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取任务运行制品", exception);
        }
    }

    private String resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/") || objectKey.contains("..")) {
            throw new IllegalArgumentException("任务运行对象 Key 无效");
        }
        return rootPrefix.isEmpty() ? objectKey : rootPrefix + "/" + objectKey;
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim();
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        return normalized;
    }
}
