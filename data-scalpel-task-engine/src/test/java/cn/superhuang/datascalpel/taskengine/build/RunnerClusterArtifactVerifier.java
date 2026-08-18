package cn.superhuang.datascalpel.taskengine.build;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;

/** Build-time smoke check for local, cluster and distribution runtime artifacts. */
public final class RunnerClusterArtifactVerifier {
    private static final String SHAPEFILE_DATA_STORE_CLASS =
            "org.geotools.data.shapefile.ShapefileDataStore";
    private static final String GEOPARQUET_FILE_FORMAT_CLASS =
            "org.apache.spark.sql.execution.datasources.geoparquet.GeoParquetFileFormat";
    private static final String CRS_CLASS = "org.geotools.referencing.CRS";
    private static final String REFERENCING_FACTORY_FINDER_CLASS =
            "org.geotools.referencing.ReferencingFactoryFinder";
    private static final String DEFERRED_AUTHORITY_FACTORY_CLASS =
            "org.geotools.referencing.factory.DeferredAuthorityFactory";
    private static final String WEAK_COLLECTION_CLEANER_CLASS =
            "org.geotools.util.WeakCollectionCleaner";
    private static final List<String> REQUIRED_ENTRIES = List.of(
            "cn/superhuang/datascalpel/taskengine/runner/TaskRunnerMain.class",
            "cn/superhuang/datascalpel/taskengine/runner/FileDatasetBatchReaderRegistry.class",
            "cn/superhuang/datascalpel/taskengine/runner/FileDatasetGeometryConverter.class",
            "cn/superhuang/data/scalpel/filegdb/FileGeodatabase.class",
            "cn/superhuang/data/scalpel/filegdb/s3/S3FileGdbSource.class",
            "cn/superhuang/data/scalpel/shapefile/ShapefileDataset.class",
            "cn/superhuang/data/scalpel/shapefile/s3/S3ShapefileSource.class",
            "org/apache/hadoop/fs/s3a/S3AFileSystem.class",
            "software/amazon/awssdk/services/s3/S3Client.class",
            "org/apache/poi/xssf/usermodel/XSSFWorkbook.class",
            "org/apache/spark/sql/v2/avro/AvroDataSourceV2.class",
            "cn/superhuang/datascalpel/sdk/SparkBatchJob.class",
            "oracle/jdbc/OracleDriver.class",
            "com/microsoft/sqlserver/jdbc/SQLServerDriver.class",
            "com/clickhouse/jdbc/ClickHouseDriver.class",
            "dm/jdbc/driver/DmDriver.class",
            "com/kingbase8/Driver.class",
            "org/opengauss/Driver.class",
            "com/taosdata/jdbc/tmq/TaosConsumer.class",
            "com/taosdata/jdbc/ws/tmq/WSConsumer.class",
            "org/java_websocket/client/WebSocketClient.class",
            "org/apache/sedona/spark/SedonaContext.class",
            "org/apache/spark/sql/sedona_sql/UDT/GeometryUDT.class",
            "org/apache/spark/sql/execution/datasources/geoparquet/GeoParquetFileFormat.class",
            "org/geotools/api/referencing/NoSuchAuthorityCodeException.class",
            "org/geotools/referencing/CRS.class",
            "org/geotools/data/shapefile/ShapefileDataStore.class"
    );

    private static final List<String> FORBIDDEN_ENTRIES = List.of(
            "org/apache/spark/sql/SparkSession.class",
            "scala/Option.class",
            "org/apache/hadoop/conf/Configuration.class"
    );

    private RunnerClusterArtifactVerifier() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 3) {
            throw new IllegalArgumentException(
                    "Expected the local runner JAR, cluster runner JAR and distribution lib paths");
        }
        Path localArtifact = requireFile(arguments[0], "Local runner JAR");
        Path clusterArtifact = requireFile(arguments[1], "Cluster runner JAR");
        Path distributionLib = Path.of(arguments[2]).toAbsolutePath().normalize();
        if (!Files.isDirectory(distributionLib)) {
            throw new IllegalStateException(
                    "Task Engine distribution lib directory does not exist: " + distributionLib);
        }

        verifyClusterEntries(clusterArtifact);
        verifyClassLoading("local runner", List.of(localArtifact));
        verifyClusterClassLoading(clusterArtifact, localArtifact);
        List<Path> distributionJars = distributionJars(distributionLib);
        verifyDistributionDependencies(distributionJars);
        verifyClassLoading("Task Engine distribution", distributionJars);
    }

    private static Path requireFile(String value, String label) {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(label + " does not exist: " + path);
        }
        return path;
    }

    private static void verifyClusterEntries(Path artifact) throws IOException {
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

    private static List<Path> distributionJars(Path lib) throws IOException {
        try (var files = Files.list(lib)) {
            List<Path> jars = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (jars.isEmpty()) {
                throw new IllegalStateException(
                        "Task Engine distribution has no runtime JARs: " + lib);
            }
            return jars;
        }
    }

    private static void verifyDistributionDependencies(List<Path> jars) {
        long wrapperCount = jars.stream().filter(path ->
                path.getFileName().toString().startsWith("geotools-wrapper-")).count();
        long shapefileCount = jars.stream().filter(path ->
                path.getFileName().toString().startsWith("gt-shapefile-")).count();
        if (wrapperCount != 1L || shapefileCount != 1L) {
            throw new IllegalStateException(
                    "Task Engine distribution must contain one GeoTools wrapper and one gt-shapefile JAR");
        }
        List<String> forbidden = jars.stream()
                .map(path -> path.getFileName().toString())
                .filter(RunnerClusterArtifactVerifier::isForbiddenGeoToolsCoreJar)
                .toList();
        if (!forbidden.isEmpty()) {
            throw new IllegalStateException(
                    "Task Engine distribution contains duplicate GeoTools core artifacts: " + forbidden);
        }
    }

    private static boolean isForbiddenGeoToolsCoreJar(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.startsWith("gt-main-")
                || normalized.startsWith("gt-referencing-")
                || normalized.startsWith("gt-metadata-")
                || normalized.startsWith("gt-api-")
                || normalized.startsWith("gt-epsg-");
    }

    private static void verifyClassLoading(String artifactName, List<Path> classpath) throws Exception {
        verifyClassLoading(artifactName, classpath, List.of());
    }

    private static void verifyClusterClassLoading(
            Path clusterArtifact,
            Path localArtifact
    ) throws Exception {
        // The cluster artifact intentionally excludes Spark. Use the local runner only as the
        // simulated cluster-provided host, while forcing all spatial runtime classes to be
        // resolved from the cluster artifact itself.
        verifyClassLoading("cluster runner", List.of(clusterArtifact), List.of(localArtifact));
    }

    private static void verifyClassLoading(
            String artifactName,
            List<Path> classpath,
            List<Path> hostClasspath
    ) throws Exception {
        URL[] urls = classpath.stream().map(path -> {
            try {
                return path.toUri().toURL();
            } catch (IOException exception) {
                throw new IllegalStateException("Invalid classpath entry: " + path, exception);
            }
        }).toArray(URL[]::new);
        URL[] hostUrls = hostClasspath.stream().map(path -> {
            try {
                return path.toUri().toURL();
            } catch (IOException exception) {
                throw new IllegalStateException("Invalid host classpath entry: " + path, exception);
            }
        }).toArray(URL[]::new);
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (URLClassLoader host = new URLClassLoader(
                hostUrls, ClassLoader.getPlatformClassLoader());
             URLClassLoader loader = hostClasspath.isEmpty()
                     ? new URLClassLoader(urls, ClassLoader.getPlatformClassLoader())
                     : new SpatialChildFirstClassLoader(urls, host)) {
            thread.setContextClassLoader(loader);
            try {
                Class.forName(SHAPEFILE_DATA_STORE_CLASS, true, loader);
                Class.forName(GEOPARQUET_FILE_FORMAT_CLASS, true, loader);
                Class.forName("org.postgresql.Driver", true, loader);
                Class.forName("com.mysql.cj.jdbc.Driver", true, loader);
                Class.forName("oracle.jdbc.OracleDriver", true, loader);
                Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver", true, loader);
                Class.forName("com.clickhouse.jdbc.ClickHouseDriver", true, loader);
                Class.forName("dm.jdbc.driver.DmDriver", true, loader);
                Class.forName("com.kingbase8.Driver", true, loader);
                Class.forName("org.opengauss.Driver", true, loader);
                Class<?> crs = Class.forName(CRS_CLASS, true, loader);
                Object decoded = crs.getMethod("decode", String.class, boolean.class)
                        .invoke(null, "EPSG:4326", true);
                if (decoded == null) {
                    throw new IllegalStateException(
                            artifactName + " could not decode EPSG:4326 through GeoTools CRS");
                }
            } finally {
                shutdownGeoToolsRuntime(loader);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    artifactName + " cannot load the spatial file output runtime", exception);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private static final class SpatialChildFirstClassLoader extends URLClassLoader {
        private SpatialChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (isSpatialRuntimeClass(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        // Dependencies supplied by Spark or Hadoop are resolved from the host.
                    }
                }
                if (loaded != null) {
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        private static boolean isSpatialRuntimeClass(String name) {
            return name.startsWith("org.apache.spark.sql.execution.datasources.geoparquet.")
                    || name.startsWith("org.apache.sedona.")
                    || name.startsWith("org.geotools.");
        }
    }

    private static void shutdownGeoToolsRuntime(ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> crs = Class.forName(CRS_CLASS, false, loader);
        crs.getMethod("cleanupThreadLocals").invoke(null);
        Class<?> factoryFinder = Class.forName(REFERENCING_FACTORY_FINDER_CLASS, false, loader);
        factoryFinder.getMethod("reset").invoke(null);
        Class<?> deferredFactory = Class.forName(DEFERRED_AUTHORITY_FACTORY_CLASS, false, loader);
        deferredFactory.getMethod("exit").invoke(null);
        Class<?> cleaner = Class.forName(WEAK_COLLECTION_CLEANER_CLASS, false, loader);
        Object defaultCleaner = cleaner.getField("DEFAULT").get(null);
        cleaner.getMethod("exit").invoke(defaultCleaner);
    }
}
