package cn.superhuang.data.scalpel.contract.execution;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QualitySampleArtifactUploadTest {
    @Test void acceptsEstablishedNestedSamplePathWithoutRelaxingOrdinaryArtifacts() {
        UUID rule = UUID.randomUUID();
        String prefix = "task-runs/" + UUID.randomUUID() + "/attempts/1/";
        String key = prefix + "quality/samples/" + rule + ".parquet";
        var sample = new QualitySampleArtifactUpload(rule, URI.create("http://localhost/sample"), key, 1024);
        assertEquals(key, sample.objectKey());
        assertThrows(IllegalArgumentException.class, () -> ExecutionContractValidation.objectKey(key));
        for (String invalid : new String[]{prefix + "quality/samples/" + UUID.randomUUID() + ".parquet",
                prefix + "quality/samples/../" + rule + ".parquet", prefix + rule + ".parquet",
                key + "/extra", key.replace("/attempts/1/", "/attempts/0/")}) {
            assertThrows(IllegalArgumentException.class, () -> new QualitySampleArtifactUpload(
                    rule, URI.create("http://localhost/sample"), invalid, 1024));
        }
    }
}
