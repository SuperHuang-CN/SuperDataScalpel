package cn.superhuang.datascalpel.taskengine.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Build-time smoke check for the Spark cluster runner artifact.
 */
public final class RunnerClusterArtifactVerifier {
    private static final List<String> REQUIRED_ENTRIES = List.of(
            "cn/superhuang/datascalpel/taskengine/runner/TaskRunnerMain.class",
            "cn/superhuang/datascalpel/taskengine/runner/FileDatasetBatchReaderRegistry.class",
            "org/apache/hadoop/fs/s3a/S3AFileSystem.class",
            "software/amazon/awssdk/services/s3/S3Client.class",
            "org/apache/poi/xssf/usermodel/XSSFWorkbook.class",
            "org/apache/spark/sql/v2/avro/AvroDataSourceV2.class"
    );

    private static final List<String> FORBIDDEN_ENTRIES = List.of(
            "org/apache/spark/sql/SparkSession.class",
            "scala/Option.class",
            "org/apache/hadoop/conf/Configuration.class"
    );

    private RunnerClusterArtifactVerifier() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the runner cluster JAR path");
        }
        Path artifact = Path.of(arguments[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(artifact)) {
            throw new IllegalStateException("Runner cluster JAR does not exist: " + artifact);
        }
        try (JarFile jar = new JarFile(artifact.toFile())) {
            for (String entry : REQUIRED_ENTRIES) {
                if (jar.getJarEntry(entry) == null) {
                    throw new IllegalStateException("Runner cluster JAR is missing required entry: " + entry);
                }
            }
            for (String entry : FORBIDDEN_ENTRIES) {
                if (jar.getJarEntry(entry) != null) {
                    throw new IllegalStateException("Runner cluster JAR contains cluster-provided entry: " + entry);
                }
            }
        }
    }
}
