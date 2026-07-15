package cn.superhuang.data.scalpel.business.filedataset.storage;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Objects;

/** S3-compatible implementation used for the system's private file-dataset bucket. */
public class S3FileObjectStorage implements FileObjectStorage {

    private final S3Client client;
    private final String bucket;
    private final String rootPrefix;

    public S3FileObjectStorage(S3Client client, String bucket, String rootPrefix) {
        this.client = Objects.requireNonNull(client, "client");
        this.bucket = requireText(bucket, "bucket");
        this.rootPrefix = normalizePrefix(rootPrefix);
    }

    @Override
    public StoredFileObject store(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        try {
            PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(bucket).key(fullKey(objectKey));
            if (contentType != null && !contentType.isBlank()) {
                request.contentType(contentType);
            }
            PutObjectResponse response = client.putObject(request.build(), RequestBody.fromInputStream(inputStream, contentLength));
            return new StoredFileObject(response.eTag());
        } catch (S3Exception exception) {
            throw new FileStorageException("S3 对象上传失败", exception);
        } catch (RuntimeException exception) {
            throw new FileStorageException("S3 对象上传失败", exception);
        }
    }

    @Override
    public FileObjectContent open(String objectKey) {
        try {
            ResponseInputStream<GetObjectResponse> response = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(fullKey(objectKey)).build()
            );
            GetObjectResponse metadata = response.response();
            return new FileObjectContent(
                    response,
                    metadata.contentLength() == null ? -1L : metadata.contentLength(),
                    metadata.contentType(),
                    response::abort
            );
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new FileStorageObjectNotFoundException("S3 对象不存在", exception);
            }
            throw new FileStorageException("S3 对象读取失败", exception);
        } catch (RuntimeException exception) {
            throw new FileStorageException("S3 对象读取失败", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(request -> request.bucket(bucket).key(fullKey(objectKey)));
        } catch (S3Exception exception) {
            throw new FileStorageException("S3 对象删除失败", exception);
        } catch (RuntimeException exception) {
            throw new FileStorageException("S3 对象删除失败", exception);
        }
    }

    private String fullKey(String objectKey) {
        String normalizedObjectKey = requireText(objectKey, "objectKey").replaceFirst("^/+", "");
        return rootPrefix.isEmpty() ? normalizedObjectKey : rootPrefix + "/" + normalizedObjectKey;
    }

    private static String normalizePrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value.trim().replaceFirst("^/+", "").replaceFirst("/+$", "");
        if (result.contains("..")) {
            throw new IllegalArgumentException("S3 根前缀不能包含上级路径");
        }
        return result;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
        return value.trim();
    }
}
