package cn.superhuang.data.scalpel.filegdb.s3;

import static org.junit.jupiter.api.Assertions.assertFalse;

import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import java.net.URI;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

class S3RemoteCompatibilityTest {
    @Test
    void readsExistingS3OrMinioFixtureWithoutMutatingIt() {
        String bucket = System.getProperty("filegdb.s3.bucket");
        String prefix = System.getProperty("filegdb.s3.prefix");
        Assumptions.assumeTrue(
                bucket != null && !bucket.isBlank() && prefix != null && !prefix.isBlank(),
                "set filegdb.s3.bucket and filegdb.s3.prefix to enable the remote compatibility test");

        String region = System.getProperty("filegdb.s3.region", "us-east-1");
        String endpoint = System.getProperty("filegdb.s3.endpoint");
        boolean pathStyle = Boolean.parseBoolean(System.getProperty("filegdb.s3.pathStyle", "false"));
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        try (S3Client client = builder.build();
                FileGeodatabase database = FileGeodatabase.open(S3FileGdbSource.create(
                        client,
                        new S3FileGdbLocation(bucket, prefix),
                        S3FileGdbOptions.defaults()))) {
            assertFalse(database.layers().isEmpty());
            database.layers().forEach(layer -> database.schema(layer.id()));
        }
    }
}
