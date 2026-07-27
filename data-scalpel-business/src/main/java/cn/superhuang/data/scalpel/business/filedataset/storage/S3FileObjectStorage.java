package cn.superhuang.data.scalpel.business.filedataset.storage;

import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.filegdb.s3.S3FileGdbLocation;
import cn.superhuang.data.scalpel.filegdb.s3.S3FileGdbOptions;
import cn.superhuang.data.scalpel.filegdb.s3.S3FileGdbSource;
import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;
import cn.superhuang.data.scalpel.shapefile.s3.S3ShapefileLocation;
import cn.superhuang.data.scalpel.shapefile.s3.S3ShapefileOptions;
import cn.superhuang.data.scalpel.shapefile.s3.S3ShapefileSource;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.Objects;
import java.util.List;
import java.util.Set;

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

    @Override
    public void deletePrefix(String prefix) {
        String fullPrefix = fullKey(prefix).replaceFirst("/+$", "") + "/";
        String continuationToken = null;
        try {
            do {
                ListObjectsV2Response response = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(fullPrefix)
                        .continuationToken(continuationToken)
                        .build());
                List<ObjectIdentifier> objects = response.contents().stream()
                        .map(item -> ObjectIdentifier.builder().key(item.key()).build())
                        .toList();
                if (!objects.isEmpty()) {
                    client.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(bucket)
                            .delete(Delete.builder().objects(objects).quiet(true).build())
                            .build());
                }
                continuationToken = Boolean.TRUE.equals(response.isTruncated())
                        ? response.nextContinuationToken() : null;
            } while (continuationToken != null);
        } catch (S3Exception exception) {
            throw new FileStorageException("S3 目录前缀删除失败", exception);
        } catch (RuntimeException exception) {
            throw new FileStorageException("S3 目录前缀删除失败", exception);
        }
    }

    @Override
    public FileGeodatabase openFileGeodatabase(String prefix) {
        return FileGeodatabase.open(S3FileGdbSource.create(
                client,
                new S3FileGdbLocation(bucket, fullKey(prefix)),
                S3FileGdbOptions.defaults()
        ));
    }

    @Override
    public ShapefileDataset openShapefile(
            String prefix,
            Set<ShapefileComponent> components,
            ShapefileOpenOptions options
    ) {
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(options, "options");
        String shpKey = fullKey(prefix).replaceFirst("/+$", "") + "/data.shp";
        S3ShapefileLocation location = S3ShapefileLocation.fromShpKey(bucket, shpKey);
        for (ShapefileComponent component : ShapefileComponent.values()) {
            if (!component.required() && !components.contains(component)) {
                location = location.withComponentKey(component, null);
            }
        }
        return ShapefileDataset.open(
                S3ShapefileSource.create(client, location, S3ShapefileOptions.defaults()),
                options
        );
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
