package cn.superhuang.data.scalpel.dispatcher.artifact;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionLaunch;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherArtifactProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Profile("!test")
public class S3DispatcherArtifactService implements DispatcherArtifactService {
    private final DispatcherArtifactProperties properties;
    private final Object monitor = new Object();
    private volatile S3Client client;
    private volatile S3Presigner presigner;

    public S3DispatcherArtifactService(DispatcherArtifactProperties properties) {
        this.properties = properties;
    }

    @Override
    public BackendReadiness readiness() {
        List<String> issues = configurationIssues();
        if (!issues.isEmpty()) return new BackendReadiness(false, issues);
        try {
            client().headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
            return BackendReadiness.up();
        } catch (RuntimeException exception) {
            return BackendReadiness.down("任务制品存储不可用");
        }
    }

    @Override
    public ArtifactLaunchAccess prepareLaunch(ExecutionLaunch launch) throws BackendException {
        if (launch == null) throw new BackendException("INVALID_EXECUTION_LAUNCH", "执行启动参数不能为空");
        List<String> issues = configurationIssues();
        if (!issues.isEmpty()) {
            throw new BackendException("ARTIFACT_STORAGE_UNAVAILABLE", issues.getFirst());
        }
        try {
            String manifestKey = resolve(launch.manifestKey());
            HeadObjectResponse manifest = client().headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket()).key(manifestKey).build());
            if (manifest.contentLength() == null || manifest.contentLength() < 1
                    || manifest.contentLength() > properties.maximumManifestBytes()) {
                throw new BackendException("INVALID_MANIFEST_OBJECT", "manifest 对象为空或超过允许大小");
            }
            java.util.List<cn.superhuang.data.scalpel.contract.execution.QualitySampleArtifactUpload> qualitySamples =
                    launch.qualitySampleRuleIds().stream().map(ruleId -> {
                        String objectKey = "task-runs/%s/attempts/%d/quality/samples/%s.parquet".formatted(
                                launch.identity().runId(), launch.identity().attempt(), ruleId);
                        return new cn.superhuang.data.scalpel.contract.execution.QualitySampleArtifactUpload(
                                ruleId, presignPut(resolve(objectKey), "application/vnd.apache.parquet"),
                                objectKey, 20 * 1024 * 1024);
                    }).toList();
            cn.superhuang.data.scalpel.contract.execution.LaunchUserJarDownload userJar = null;
            if (launch.userJar() != null) {
                String jarKey = resolve(launch.userJar().objectKey());
                HeadObjectResponse jar = client().headObject(HeadObjectRequest.builder()
                        .bucket(properties.bucket()).key(jarKey).build());
                if (jar.contentLength() == null || jar.contentLength() != launch.userJar().sizeBytes()) {
                    throw new BackendException("INVALID_USER_JAR_OBJECT", "用户 JAR 对象大小不一致");
                }
                userJar = new cn.superhuang.data.scalpel.contract.execution.LaunchUserJarDownload(
                        presignGet(jarKey), launch.userJar().sha256(), launch.userJar().sizeBytes());
            }
            return new ArtifactLaunchAccess(
                    presignGet(manifestKey),
                    presignPut(resolve(launch.resultKey()), "application/json"),
                    presignPut(resolve(launch.logKey()), "text/plain; charset=utf-8"),
                    Math.toIntExact(properties.maximumManifestBytes()), qualitySamples, userJar
            );
        } catch (BackendException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BackendException("ARTIFACT_STORAGE_UNAVAILABLE", "无法读取或签发任务制品地址", exception);
        }
    }

    @Override
    public Optional<byte[]> readIfPresent(String objectKey, int maximumBytes) throws BackendException {
        if (maximumBytes < 1 || maximumBytes > 20 * 1024 * 1024) {
            throw new BackendException("INVALID_ARTIFACT_LIMIT", "任务制品读取大小限制无效");
        }
        try (var response = client().getObject(GetObjectRequest.builder()
                .bucket(properties.bucket()).key(resolve(objectKey)).build())) {
            Long contentLength = response.response().contentLength();
            if (contentLength == null || contentLength < 1 || contentLength > maximumBytes) {
                throw new BackendException("INVALID_ARTIFACT_OBJECT", "任务制品为空或超过允许大小");
            }
            byte[] content = response.readAllBytes();
            if (content.length > maximumBytes) {
                throw new BackendException("INVALID_ARTIFACT_OBJECT", "任务制品超过允许大小");
            }
            return Optional.of(content);
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return Optional.empty();
            throw new BackendException("ARTIFACT_STORAGE_UNAVAILABLE", "无法读取任务制品", exception);
        } catch (IOException exception) {
            throw new BackendException("ARTIFACT_STORAGE_UNAVAILABLE", "无法读取任务制品", exception);
        }
    }

    @Override
    public void store(String objectKey, byte[] content, String contentType) throws BackendException {
        if (content == null || contentType == null || contentType.isBlank()) {
            throw new BackendException("INVALID_ARTIFACT_OBJECT", "任务制品内容无效");
        }
        try {
            client().putObject(PutObjectRequest.builder()
                    .bucket(properties.bucket()).key(resolve(objectKey)).contentType(contentType).build(),
                    RequestBody.fromBytes(content));
        } catch (RuntimeException exception) {
            throw new BackendException("ARTIFACT_STORAGE_UNAVAILABLE", "无法写入任务制品", exception);
        }
    }

    @PreDestroy
    void close() {
        S3Client currentClient = client;
        if (currentClient != null) currentClient.close();
        S3Presigner currentPresigner = presigner;
        if (currentPresigner != null) currentPresigner.close();
    }

    private URI presignGet(String key) {
        var request = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                .bucket(properties.bucket()).key(key).build();
        return URI.create(presigner().presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(properties.urlLifetime()).getObjectRequest(request).build()).url().toString());
    }

    private URI presignPut(String key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket()).key(key).contentType(contentType).build();
        return URI.create(presigner().presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.urlLifetime()).putObjectRequest(request).build()).url().toString());
    }

    private S3Client client() {
        S3Client current = client;
        if (current != null) return current;
        synchronized (monitor) {
            if (client == null) {
                client = S3Client.builder()
                        .endpointOverride(endpoint(properties.endpoint(), "S3 endpoint"))
                        .region(Region.of(properties.region()))
                        .credentialsProvider(credentials())
                        .serviceConfiguration(serviceConfiguration())
                        .build();
            }
            return client;
        }
    }

    private S3Presigner presigner() {
        S3Presigner current = presigner;
        if (current != null) return current;
        synchronized (monitor) {
            if (presigner == null) {
                presigner = S3Presigner.builder()
                        .endpointOverride(endpoint(properties.effectiveRunnerEndpoint(), "S3 Runner endpoint"))
                        .region(Region.of(properties.region()))
                        .credentialsProvider(credentials())
                        .serviceConfiguration(serviceConfiguration())
                        .build();
            }
            return presigner;
        }
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                properties.accessKey().trim(), properties.secretKey().trim()));
    }

    private S3Configuration serviceConfiguration() {
        return S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyleAccess()).build();
    }

    private List<String> configurationIssues() {
        List<String> issues = new ArrayList<>();
        if (!properties.configured()) issues.add("任务制品存储配置不完整");
        try {
            if (properties.configured()) {
                endpoint(properties.endpoint(), "S3 endpoint");
                endpoint(properties.effectiveRunnerEndpoint(), "S3 Runner endpoint");
            }
        } catch (IllegalArgumentException exception) {
            issues.add("任务制品存储地址无效");
        }
        return List.copyOf(issues);
    }

    private String resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")
                || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new IllegalArgumentException("任务制品对象 Key 无效");
        }
        return properties.rootPrefix().isEmpty() ? objectKey : properties.rootPrefix() + "/" + objectKey;
    }

    private static URI endpoint(String value, String field) {
        URI uri = URI.create(value.trim());
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(field + " 必须是 HTTP(S) URL");
        }
        return uri;
    }
}
