package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarDevelopmentKitJob;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskDefinition;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskResourceBindingRepository;
import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseStandardQueryExecutor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SparkJarDevelopmentKitEntryTest {
    @ParameterizedTest @EnumSource(SparkJarJobMode.class)
    void renamedDraftIsExportedWithMatchingSourceManifestAndTestReference(SparkJarJobMode mode) throws Exception {
        UUID task = UUID.randomUUID();
        var definitions = mock(SparkJarTaskDefinitionRepository.class);
        var definition = SparkJarTaskDefinition.create(task, mode);
        String source = "package demo.jobs; public class AssetJob {}";
        definition.saveOnlineSource(source);
        when(definitions.findByTaskId(task)).thenReturn(Optional.of(definition));
        var generator = new SparkJarDevelopmentKitGenerator(definitions, mock(SparkJarTaskResourceBindingRepository.class),
                mock(DataModelRepository.class), mock(DataModelFieldRepository.class), mock(DataSourceRepository.class),
                mock(DatabaseStandardQueryExecutor.class), mock(DialectRegistry.class), mock(DatabaseInspector.class),
                JsonMapper.builderWithJackson2Defaults().build());
        var job = SparkJarDevelopmentKitJob.queue(task, "test", 1, "{\"samples\":[],\"jdbcTables\":[]}", "test", Instant.now());
        try (var kit = generator.generate(job, (stage, percent, resource) -> {}); var zip = new ZipFile(kit.zip().toFile())) {
            String prefix = "datascalpel-spark-job/";
            assertEquals(source, new String(zip.getInputStream(zip.getEntry(prefix + "src/main/java/demo/jobs/AssetJob.java")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(new String(zip.getInputStream(zip.getEntry(prefix + "pom.xml")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("demo.jobs.AssetJob"));
            String testClass = mode == SparkJarJobMode.BATCH ? "ExampleSparkJobTest" : "ExampleSparkStreamingJobTest";
            assertTrue(new String(zip.getInputStream(zip.getEntry(prefix + "src/test/java/com/example/datascalpel/" + testClass + ".java")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("new demo.jobs.AssetJob()"));
        }
    }

    @Test void commentsCannotChooseExportPath() throws Exception {
        assertEquals("demo.Actual", SparkJarDevelopmentKitGenerator.developmentEntry(
                "// package fake; public class Fake {}\npackage demo; public class Actual {}", "fallback.Job"));
        assertEquals("fallback.Job", SparkJarDevelopmentKitGenerator.developmentEntry("public class Unfinished {", "fallback.Job"));
    }
}
