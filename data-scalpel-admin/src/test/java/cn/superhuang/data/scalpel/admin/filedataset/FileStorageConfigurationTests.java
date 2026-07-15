package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.S3FileObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "data-scalpel.file-storage.s3.endpoint=https://object-storage.internal:9000",
        "data-scalpel.file-storage.s3.region=us-east-1",
        "data-scalpel.file-storage.s3.bucket=datascalpel",
        "data-scalpel.file-storage.s3.root-prefix=data-scalpel",
        "data-scalpel.file-storage.s3.access-key=test-access-key",
        "data-scalpel.file-storage.s3.secret-key=test-secret-key",
        "data-scalpel.file-storage.s3.path-style-access=true"
})
class FileStorageConfigurationTests {

    @Autowired
    private FileObjectStorage fileObjectStorage;

    @Test
    void createsTheS3CompatibleStorageAdapterWhenRuntimePropertiesAreConfigured() {
        assertInstanceOf(S3FileObjectStorage.class, fileObjectStorage);
    }
}
