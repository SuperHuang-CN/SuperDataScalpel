package cn.superhuang.datascalpel.taskengine.compiler;

import cn.superhuang.data.scalpel.contract.task.SparkJarSourceCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.SparkJarSourceCompilationResponse;
import cn.superhuang.data.scalpel.contract.task.SparkJarSourceDiagnostic;
import cn.superhuang.data.scalpel.contract.task.SparkJarSourceDiagnosticSeverity;
import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;
import cn.superhuang.datascalpel.taskengine.http.TaskEngineException;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/** Compiles one fixed batch or streaming Spark job source file without loading user classes. */
final class SparkJarOnlineSourceCompiler {
    static final int MAX_SOURCE_BYTES = 256 * 1024;
    static final int MAX_JAR_BYTES = 5 * 1024 * 1024;
    static final int MAX_DIAGNOSTICS = 200;
    static final String BATCH_JOB_CLASS = "com.example.datascalpel.ExampleSparkJob";
    static final String STREAMING_JOB_CLASS = "com.example.datascalpel.ExampleSparkStreamingJob";
    private static final String CHECK_PATH = "cn/superhuang/datascalpel/onlinecheck/OnlineSourceContractCheck.java";
    private static final Set<String> ALLOWED_CLASSPATH_PREFIXES = Set.of(
            "data-scalpel-task-sdk-", "spark-", "scala-library-", "scala-reflect-",
            "scala-collection-compat_", "slf4j-api-", "jakarta.annotation-api-", "javax.annotation-api-");

    SparkJarSourceCompilationResponse compile(SparkJarSourceCompilationRequest request, long startedNanos) {
        String source = validateAndNormalize(request);
        SparkJarJobMode mode = request.jobMode();
        String jobClass = jobClass(mode);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || Runtime.version().feature() != 21) {
            throw problem(503, "ONLINE_COMPILER_UNAVAILABLE", "在线编译不可用",
                    "Task Engine 必须使用完整的 JDK 21 运行");
        }
        Path work = null;
        try {
            work = Files.createTempDirectory("datascalpel-online-spark-");
            Path sourceRoot = Files.createDirectories(work.resolve("src"));
            Path classes = Files.createDirectories(work.resolve("classes"));
            Path sourceFile = write(sourceRoot, sourcePath(mode), source);
            Path checkFile = write(sourceRoot, CHECK_PATH, contractCheck(mode));
            DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
            boolean successful;
            try (StandardJavaFileManager files = compiler.getStandardFileManager(collector, Locale.SIMPLIFIED_CHINESE,
                    StandardCharsets.UTF_8)) {
                files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
                List<Path> classpath = compilerClasspath();
                if (classpath.isEmpty()) {
                    throw problem(503, "ONLINE_COMPILER_CLASSPATH_UNAVAILABLE", "在线编译不可用",
                            "Task Engine 未找到 Spark 与 SDK 编译依赖");
                }
                files.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);
                Iterable<? extends JavaFileObject> units = files.getJavaFileObjects(sourceFile, checkFile);
                successful = Boolean.TRUE.equals(compiler.getTask(null, files, collector,
                        List.of("--release", "21", "-proc:none", "-encoding", "UTF-8", "-parameters"),
                        null, units).call());
            }
            List<SparkJarSourceDiagnostic> diagnostics = diagnostics(
                    collector.getDiagnostics(), source, mode);
            long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            String sourceSha = sha256(source.getBytes(StandardCharsets.UTF_8));
            if (!successful) {
                return new SparkJarSourceCompilationResponse(request.requestId(), false, duration, sourceSha,
                        null, null, diagnostics);
            }
            byte[] jar = createJar(classes, mode);
            if (jar.length > MAX_JAR_BYTES) {
                throw problem(413, "ONLINE_JAR_TOO_LARGE", "在线编译产物过大", "编译产物不能超过 5 MiB");
            }
            return new SparkJarSourceCompilationResponse(request.requestId(), true, duration, sourceSha,
                    sha256(jar), jar, diagnostics);
        } catch (TaskEngineException exception) {
            throw exception;
        } catch (IOException exception) {
            throw problem(500, "ONLINE_COMPILATION_IO_FAILED", "在线编译失败", "无法创建在线编译制品");
        } finally {
            deleteRecursively(work);
        }
    }

    private static String validateAndNormalize(SparkJarSourceCompilationRequest request) {
        if (request == null || request.requestId() == null) {
            throw problem(400, "INVALID_REQUEST", "请求无效", "requestId 不能为空");
        }
        if (request.sourceCode() == null || request.sourceCode().isBlank()) {
            throw problem(400, "ONLINE_SOURCE_EMPTY", "在线源码无效", "在线源码不能为空");
        }
        String source = request.sourceCode().replace("\r\n", "\n").replace('\r', '\n');
        if (source.indexOf('\0') >= 0 || source.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) {
            throw problem(400, "ONLINE_SOURCE_TOO_LARGE", "在线源码无效", "在线源码不能包含 NUL 且不能超过 256 KiB");
        }
        return source;
    }

    private static Path write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static List<Path> compilerClasspath() {
        String raw = System.getProperty("java.class.path", "");
        List<Path> paths = new ArrayList<>();
        for (String item : raw.split(java.io.File.pathSeparator)) {
            if (item.isBlank()) continue;
            Path path = Path.of(item).toAbsolutePath().normalize();
            String name = path.getFileName() == null ? "" : path.getFileName().toString();
            boolean sdkClasses = Files.isDirectory(path)
                    && path.toString().replace('\\', '/').contains("/data-scalpel-task-sdk/target/classes");
            boolean allowedJar = name.endsWith(".jar")
                    && ALLOWED_CLASSPATH_PREFIXES.stream().anyMatch(name::startsWith);
            if (sdkClasses || allowedJar) paths.add(path);
        }
        return List.copyOf(paths);
    }

    private static List<SparkJarSourceDiagnostic> diagnostics(
            List<Diagnostic<? extends JavaFileObject>> values,
            String source,
            SparkJarJobMode mode
    ) {
        List<SparkJarSourceDiagnostic> result = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> value : values) {
            if (result.size() >= MAX_DIAGNOSTICS) break;
            boolean generatedCheck = value.getSource() != null
                    && value.getSource().getName().replace('\\', '/').endsWith(CHECK_PATH);
            long line = generatedCheck ? 1 : positive(value.getLineNumber());
            long column = generatedCheck ? 1 : positive(value.getColumnNumber());
            long[] end = generatedCheck ? new long[]{line, column}
                    : endPosition(source, value.getEndPosition(), line, column);
            String message = generatedCheck
                    ? "主类必须是公开的 " + jobClass(mode) + "，实现 "
                    + (mode == SparkJarJobMode.STREAMING ? "SparkStreamingJob" : "SparkBatchJob")
                    + " 并提供公开无参构造器"
                    : limit(value.getMessage(Locale.SIMPLIFIED_CHINESE), 2_000);
            String code = generatedCheck ? "ONLINE_MAIN_CLASS_CONTRACT" : limit(value.getCode(), 200);
            result.add(new SparkJarSourceDiagnostic(severity(value.getKind()), code,
                    message, line, column, end[0], end[1]));
        }
        return List.copyOf(result);
    }

    private static SparkJarSourceDiagnosticSeverity severity(Diagnostic.Kind kind) {
        return switch (kind) {
            case ERROR -> SparkJarSourceDiagnosticSeverity.ERROR;
            case WARNING, MANDATORY_WARNING -> SparkJarSourceDiagnosticSeverity.WARNING;
            default -> SparkJarSourceDiagnosticSeverity.NOTE;
        };
    }

    private static long positive(long value) {
        return value < 1 ? 1 : value;
    }

    private static long[] endPosition(String source, long offset, long fallbackLine, long fallbackColumn) {
        if (offset < 0 || offset > source.length()) return new long[]{fallbackLine, fallbackColumn};
        long line = 1;
        long column = 1;
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '\n') { line++; column = 1; }
            else column++;
        }
        return new long[]{line, column};
    }

    private static byte[] createJar(Path classes, SparkJarJobMode mode) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(output)) {
            add(jar, "META-INF/MANIFEST.MF", ("Manifest-Version: 1.0\r\n"
                    + "DataScalpel-Job-Api-Version: 1\r\n"
                    + "DataScalpel-Job-Class: " + jobClass(mode) + "\r\n"
                    + "DataScalpel-Job-Mode: " + mode.name() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            try (Stream<Path> files = Files.walk(classes)) {
                List<Path> entries = files.filter(Files::isRegularFile)
                        .filter(path -> !classes.relativize(path).toString().replace('\\', '/')
                                .startsWith("cn/superhuang/datascalpel/onlinecheck/"))
                        .sorted(Comparator.comparing(path -> classes.relativize(path).toString()))
                        .toList();
                for (Path file : entries) {
                    add(jar, classes.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
                }
            }
        }
        return output.toByteArray();
    }

    private static String jobClass(SparkJarJobMode mode) {
        return mode == SparkJarJobMode.STREAMING ? STREAMING_JOB_CLASS : BATCH_JOB_CLASS;
    }

    private static String sourcePath(SparkJarJobMode mode) {
        return jobClass(mode).replace('.', '/') + ".java";
    }

    private static String contractCheck(SparkJarJobMode mode) {
        String interfaceName = mode == SparkJarJobMode.STREAMING
                ? "SparkStreamingJob" : "SparkBatchJob";
        return """
                package cn.superhuang.datascalpel.onlinecheck;

                public final class OnlineSourceContractCheck {
                    private final cn.superhuang.datascalpel.sdk.%s job =
                            new %s();
                }
                """.formatted(interfaceName, jobClass(mode));
    }

    private static void add(JarOutputStream jar, String name, byte[] content) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        jar.putNextEntry(entry);
        jar.write(content);
        jar.closeEntry();
    }

    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private static String limit(String value, int maximum) {
        if (value == null) return "";
        return value.length() <= maximum ? value : value.substring(0, maximum) + "…";
    }

    private static TaskEngineException problem(int status, String code, String title, String detail) {
        return new TaskEngineException(status, code, title, detail);
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) return;
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir); return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Temporary compiler work is best-effort cleanup and never contains credentials.
        }
    }
}
