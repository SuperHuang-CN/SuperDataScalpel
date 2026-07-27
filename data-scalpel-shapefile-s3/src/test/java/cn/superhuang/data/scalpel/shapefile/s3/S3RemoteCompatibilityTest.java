package cn.superhuang.data.scalpel.shapefile.s3;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
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
        String bucket = System.getProperty("shapefile.s3.bucket");
        String shpKey = System.getProperty("shapefile.s3.shpKey");
        Assumptions.assumeTrue(
                bucket != null && !bucket.isBlank() && shpKey != null && !shpKey.isBlank(),
                "set shapefile.s3.bucket and shapefile.s3.shpKey to enable the remote compatibility test");

        String region = System.getProperty("shapefile.s3.region", "us-east-1");
        String endpoint = System.getProperty("shapefile.s3.endpoint");
        boolean pathStyle = Boolean.parseBoolean(System.getProperty("shapefile.s3.pathStyle", "false"));
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        try (S3Client client = builder.build();
                ShapefileDataset dataset = ShapefileDataset.open(S3ShapefileSource.create(
                        client,
                        S3ShapefileLocation.fromShpKey(bucket, shpKey),
                        S3ShapefileOptions.defaults()))) {
            assertTrue(dataset.schema().recordCount() >= 0);
        }
    }
}
