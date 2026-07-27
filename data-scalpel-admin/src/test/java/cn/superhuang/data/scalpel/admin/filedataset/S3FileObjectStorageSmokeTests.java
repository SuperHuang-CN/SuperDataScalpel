package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageObjectNotFoundException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in smoke test for the real S3-compatible storage. It only runs when all required
 * DATASCALPEL_FILE_STORAGE_* environment variables are present and removes its object afterwards.
 */
class S3FileObjectStorageSmokeTests {

    private static final String ENDPOINT = "DATASCALPEL_FILE_STORAGE_ENDPOINT";
    private static final String BUCKET = "DATASCALPEL_FILE_STORAGE_BUCKET";
    private static final String ACCESS_KEY = "DATASCALPEL_FILE_STORAGE_ACCESS_KEY";
    private static final String SECRET_KEY = "DATASCALPEL_FILE_STORAGE_SECRET_KEY";
    private static final String ROOT_PREFIX = "data-scalpel-smoke";
    private static final String SMOKE_OBJECTS_PREFIX = ROOT_PREFIX + "/file-datasets/";

    @Test
    void uploadsReadsReplacesAndDeletesTemporaryCsvObject() throws Exception {
        assumeTrue(requiredEnvironmentIsPresent(),
                "Set the four DATASCALPEL_FILE_STORAGE_* variables to run the real storage smoke test");

        S3FileStorageProperties properties = new S3FileStorageProperties(
                System.getenv(ENDPOINT),
                environmentOrDefault("DATASCALPEL_FILE_STORAGE_REGION", "us-east-1"),
                System.getenv(BUCKET),
                ROOT_PREFIX,
                System.getenv(ACCESS_KEY),
                System.getenv(SECRET_KEY),
                true,
                null
        );
        FileStorageConfiguration configuration = new FileStorageConfiguration();
        String objectKey = "file-datasets/" + UUID.randomUUID() + "/orders.csv";

        try (S3Client client = configuration.fileDatasetS3Client(properties)) {
            deleteSmokeObjects(client, properties.bucket());
            FileObjectStorage storage = configuration.fileObjectStorage(client, properties);
            try {
                byte[] original = "order_id,amount\n1001,88.50\n".getBytes(StandardCharsets.UTF_8);
                storage.store(
                        objectKey, new ByteArrayInputStream(original), original.length, "text/csv"
                );
                assertContent(storage, objectKey, original);

                byte[] replacement = "order_id,amount\n1002,199.00\n".getBytes(StandardCharsets.UTF_8);
                storage.store(objectKey, new ByteArrayInputStream(replacement), replacement.length, "text/csv");
                assertContent(storage, objectKey, replacement);
            } finally {
                try {
                    storage.delete(objectKey);
                } finally {
                    deleteSmokeObjects(client, properties.bucket());
                }
            }

            assertThrows(FileStorageObjectNotFoundException.class, () -> storage.open(objectKey));
            assertTrue(client.listObjectsV2(request -> request
                    .bucket(properties.bucket()).prefix(SMOKE_OBJECTS_PREFIX)).contents().isEmpty());
        }
    }

    private static void deleteSmokeObjects(S3Client client, String bucket) {
        client.listObjectsV2(request -> request.bucket(bucket).prefix(SMOKE_OBJECTS_PREFIX))
                .contents()
                .forEach(object -> client.deleteObject(request -> request.bucket(bucket).key(object.key())));
    }

    private static void assertContent(FileObjectStorage storage, String objectKey, byte[] expected) throws Exception {
        FileObjectStorage.FileObjectContent content = storage.open(objectKey);
        try (var inputStream = content.inputStream()) {
            assertEquals("text/csv", content.contentType());
            assertEquals(expected.length, content.contentLength());
            assertEquals(new String(expected, StandardCharsets.UTF_8),
                    new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static boolean requiredEnvironmentIsPresent() {
        return hasText(System.getenv(ENDPOINT))
                && hasText(System.getenv(BUCKET))
                && hasText(System.getenv(ACCESS_KEY))
                && hasText(System.getenv(SECRET_KEY));
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return hasText(value) ? value : defaultValue;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
