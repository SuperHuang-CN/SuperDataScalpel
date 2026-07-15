package cn.superhuang.data.scalpel.business.filedataset.service;

import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

/** Materializes seekable parser inputs without exposing object storage to parsers. */
@Component
public class FileDatasetTemporaryFileManager {

    private static final Logger log = LoggerFactory.getLogger(FileDatasetTemporaryFileManager.class);
    private static final String TEMPORARY_FILE_PREFIX = "data-scalpel-file-parsing-";

    private final Path temporaryDirectory;
    private final long maxMaterializedSize;
    private final Duration orphanRetention;

    public FileDatasetTemporaryFileManager(
            @Value("${data-scalpel.file-parsing.temporary-directory:${java.io.tmpdir}/data-scalpel/file-parsing}")
            String temporaryDirectory,
            @Value("${data-scalpel.file-parsing.max-materialized-size:1GB}") DataSize maxMaterializedSize,
            @Value("${data-scalpel.file-parsing.orphan-retention:24h}") Duration orphanRetention
    ) {
        this.temporaryDirectory = Path.of(temporaryDirectory).toAbsolutePath().normalize();
        this.maxMaterializedSize = maxMaterializedSize.toBytes();
        this.orphanRetention = orphanRetention;
        if (this.maxMaterializedSize < 1) {
            throw new IllegalArgumentException("文件解析临时文件大小上限必须大于零");
        }
        if (orphanRetention.isNegative() || orphanRetention.isZero()) {
            throw new IllegalArgumentException("文件解析临时文件清理周期必须大于零");
        }
    }

    public Path materialize(InputStream inputStream, long contentLength) throws IOException {
        if (contentLength > maxMaterializedSize) {
            throw new FileDatasetParsingException("文件超过解析大小上限");
        }
        Files.createDirectories(temporaryDirectory);
        cleanExpiredFiles();
        Path path = Files.createTempFile(temporaryDirectory, TEMPORARY_FILE_PREFIX, ".bin");
        try {
            copyWithinLimit(inputStream, path);
            return path;
        } catch (IOException | RuntimeException exception) {
            delete(path);
            throw exception;
        }
    }

    public void delete(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn("删除文件解析临时文件失败", exception);
        }
    }

    private void copyWithinLimit(InputStream inputStream, Path destination) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long copied = 0;
        try (var outputStream = Files.newOutputStream(destination, StandardOpenOption.WRITE)) {
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                if (read > maxMaterializedSize - copied) {
                    throw new FileDatasetParsingException("文件超过解析大小上限");
                }
                outputStream.write(buffer, 0, read);
                copied += read;
            }
        }
    }

    private void cleanExpiredFiles() {
        Instant threshold = Instant.now().minus(orphanRetention);
        try (Stream<Path> paths = Files.list(temporaryDirectory)) {
            paths.filter(path -> path.getFileName().toString().startsWith(TEMPORARY_FILE_PREFIX))
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant().isBefore(threshold);
                        } catch (IOException exception) {
                            return false;
                        }
                    })
                    .forEach(this::delete);
        } catch (IOException exception) {
            log.warn("清理过期文件解析临时文件失败", exception);
        }
    }
}
