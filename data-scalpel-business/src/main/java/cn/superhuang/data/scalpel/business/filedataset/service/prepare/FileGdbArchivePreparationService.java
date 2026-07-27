package cn.superhuang.data.scalpel.business.filedataset.service.prepare;

import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetTemporaryFileManager;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingInfrastructureException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageException;
import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Safely validates and publishes one uploaded ZIP as an immutable FileGDB object prefix. */
@Component
public class FileGdbArchivePreparationService implements FileDatasetPreparationService {

    private static final Logger log = LoggerFactory.getLogger(FileGdbArchivePreparationService.class);

    private final ObjectProvider<FileObjectStorage> storageProvider;
    private final FileDatasetTemporaryFileManager temporaryFileManager;
    private final int maxEntries;
    private final long maxEntrySize;
    private final long maxExpandedSize;
    private final double maxCompressionRatio;

    public FileGdbArchivePreparationService(
            ObjectProvider<FileObjectStorage> storageProvider,
            FileDatasetTemporaryFileManager temporaryFileManager,
            @Value("${data-scalpel.file-parsing.gdb.max-entries:10000}") int maxEntries,
            @Value("${data-scalpel.file-parsing.gdb.max-entry-size:1GB}") DataSize maxEntrySize,
            @Value("${data-scalpel.file-parsing.gdb.max-expanded-size:4GB}") DataSize maxExpandedSize,
            @Value("${data-scalpel.file-parsing.gdb.max-compression-ratio:200}") double maxCompressionRatio
    ) {
        this.storageProvider = storageProvider;
        this.temporaryFileManager = temporaryFileManager;
        this.maxEntries = maxEntries;
        this.maxEntrySize = maxEntrySize.toBytes();
        this.maxExpandedSize = maxExpandedSize.toBytes();
        this.maxCompressionRatio = maxCompressionRatio;
        if (maxEntries < 1 || this.maxEntrySize < 1 || this.maxExpandedSize < 1 || maxCompressionRatio < 1) {
            throw new IllegalArgumentException("GDB ZIP 安全限制必须大于零");
        }
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.GDB;
    }

    @Override
    public FileDatasetPreparationResult prepare(FileDatasetPreparationInput input) throws IOException {
        if (!supports(input.format())) {
            throw new IllegalArgumentException("GDB 准备器不支持当前文件格式");
        }
        FileObjectStorage storage = requireStorage();
        deletePrefixQuietly(storage, input.materializedPrefix(), "重试 GDB 准备任务前清理旧前缀");
        FileObjectStorage.FileObjectContent raw = storage.open(input.rawObjectKey());
        Path archive = null;
        boolean completed = false;
        try (raw) {
            archive = temporaryFileManager.materialize(
                    raw.inputStream(), raw.contentLength() >= 0 ? raw.contentLength() : input.rawSizeBytes()
            );
            Manifest manifest = inspect(archive);
            publish(storage, archive, input.materializedPrefix(), manifest);
            List<DiscoveredLayer> layers = validateAndDiscover(storage, input.materializedPrefix());
            completed = true;
            return new FileDatasetPreparationResult(
                    input.materializedPrefix(), manifest.expandedSizeBytes(), manifest.entries().size(),
                    layers.stream().map(layer -> new DiscoveredTable(
                            layer.sourceKey(), layer.sourceName(), layer.sourceOrder()
                    )).toList()
            );
        } catch (ZipException exception) {
            throw new FileDatasetParsingException("上传内容不是有效的 ZIP 归档", exception);
        } catch (RuntimeException | IOException exception) {
            deletePrefixQuietly(storage, input.materializedPrefix(), "GDB 准备失败后的前缀清理");
            throw exception;
        } finally {
            temporaryFileManager.delete(archive);
            if (!completed) {
                abortQuietly(raw);
            }
        }
    }

    @Override
    public void discard(String materializedPrefix) {
        deletePrefixQuietly(requireStorage(), materializedPrefix, "丢弃过期 GDB 物化结果");
    }

    private Manifest inspect(Path archive) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<ArchiveEntry> entries = new ArrayList<>();
            Set<String> normalizedPaths = new HashSet<>();
            String gdbRoot = null;
            long totalExpanded = 0;
            boolean markerFound = false;
            int inspectedEntryCount = 0;
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++inspectedEntryCount > maxEntries) {
                    throw invalid("ZIP 条目数量超过允许上限");
                }
                NormalizedEntry normalized = normalize(entry);
                if (normalized.ignored()) {
                    continue;
                }
                if (normalized.gdbRoot() == null) {
                    if (!entry.isDirectory()) {
                        throw invalid("ZIP 中只允许包含一个 .gdb 目录");
                    }
                    continue;
                }
                if (gdbRoot == null) {
                    gdbRoot = normalized.gdbRoot();
                } else if (!gdbRoot.equals(normalized.gdbRoot())) {
                    throw invalid("ZIP 中只能包含一个 .gdb 目录");
                }
                if (entry.isDirectory() || normalized.relativePath().isEmpty()) {
                    continue;
                }
                String duplicateKey = normalized.relativePath().toLowerCase(Locale.ROOT);
                if (!normalizedPaths.add(duplicateKey)) {
                    throw invalid("ZIP 中存在重复的 GDB 文件路径");
                }
                long size = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                if (size < 0 || compressedSize < 0) {
                    throw invalid("ZIP 条目缺少可验证的大小信息");
                }
                if (size > maxEntrySize) {
                    throw invalid("ZIP 中单个文件超过允许大小");
                }
                if (size > maxExpandedSize - totalExpanded) {
                    throw invalid("ZIP 解压后总大小超过允许上限");
                }
                if (size > 0 && (compressedSize == 0 || (double) size / compressedSize > maxCompressionRatio)) {
                    throw invalid("ZIP 条目压缩比超过安全上限");
                }
                if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) {
                    throw invalid("ZIP 使用了不支持的压缩或加密方式");
                }
                totalExpanded += size;
                markerFound |= normalized.relativePath().equalsIgnoreCase("gdb");
                entries.add(new ArchiveEntry(entry.getName(), normalized.relativePath(), size));
            }
            if (gdbRoot == null || entries.isEmpty()) {
                throw invalid("ZIP 中没有找到 .gdb 目录");
            }
            if (!markerFound) {
                throw invalid(".gdb 目录缺少 gdb 标记文件");
            }
            return new Manifest(List.copyOf(entries), totalExpanded);
        }
    }

    private void publish(FileObjectStorage storage, Path archive, String prefix, Manifest manifest) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (ArchiveEntry item : manifest.entries()) {
                ZipEntry entry = zip.getEntry(item.entryName());
                if (entry == null) {
                    throw invalid("ZIP 中的 GDB 文件清单在读取时发生变化");
                }
                try (InputStream inputStream = zip.getInputStream(entry)) {
                    storage.store(
                            prefix + "/" + item.relativePath(),
                            inputStream,
                            item.sizeBytes(),
                            "application/octet-stream"
                    );
                }
            }
        }
    }

    private List<DiscoveredLayer> validateAndDiscover(FileObjectStorage storage, String prefix) {
        try (FileGeodatabase database = storage.openFileGeodatabase(prefix)) {
            List<DiscoveredLayer> layers = new ArrayList<>();
            int sourceOrder = 0;
            for (FileGdbLayer layer : database.layers()) {
                if (!layer.systemTable()) {
                    layers.add(new DiscoveredLayer(
                            layer.id(), layer.name(), sourceOrder++, layer.type().name(), layer.type().isCursorReadable()
                    ));
                }
            }
            if (layers.isEmpty()) {
                throw invalid("GDB 中没有可管理的业务图层或表");
            }
            return List.copyOf(layers);
        }
    }

    private static NormalizedEntry normalize(ZipEntry entry) {
        String name = entry.getName();
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.contains("\\")) {
            throw invalid("ZIP 条目路径无效");
        }
        if (name.startsWith("/") || name.chars().anyMatch(Character::isISOControl)) {
            throw invalid("ZIP 条目路径无效");
        }
        String path = entry.isDirectory() ? name.replaceFirst("/+$", "") : name;
        String[] segments = path.split("/", -1);
        if (segments.length > 0
                && segments[0].length() == 2
                && Character.isLetter(segments[0].charAt(0))
                && segments[0].charAt(1) == ':') {
            throw invalid("ZIP 条目路径无效");
        }
        int gdbIndex = -1;
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw invalid("ZIP 条目包含不安全路径");
            }
            if (segment.toLowerCase(Locale.ROOT).endsWith(".gdb")) {
                if (gdbIndex >= 0) {
                    throw invalid("ZIP 条目包含嵌套的 .gdb 目录");
                }
                gdbIndex = index;
            }
        }
        boolean ignored = gdbIndex < 0 && isAllowedMetadata(segments, entry.isDirectory());
        if (gdbIndex < 0) {
            return new NormalizedEntry(null, "", ignored);
        }
        String gdbRoot = String.join("/", java.util.Arrays.copyOfRange(segments, 0, gdbIndex + 1));
        String relativePath = gdbIndex + 1 >= segments.length
                ? "" : String.join("/", java.util.Arrays.copyOfRange(segments, gdbIndex + 1, segments.length));
        return new NormalizedEntry(gdbRoot, relativePath, false);
    }

    private static boolean isAllowedMetadata(String[] segments, boolean directory) {
        if (directory) {
            return true;
        }
        for (String segment : segments) {
            if (segment.equals("__MACOSX") || segment.equals(".DS_Store")) {
                return true;
            }
        }
        return false;
    }

    private FileObjectStorage requireStorage() {
        FileObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new FileDatasetParsingInfrastructureException("文件对象存储尚未配置");
        }
        return storage;
    }

    private static void abortQuietly(FileObjectStorage.FileObjectContent content) {
        try {
            content.abort();
        } catch (RuntimeException exception) {
            log.warn("中止 GDB 原始 ZIP 读取失败", exception);
        }
    }

    private static void deletePrefixQuietly(FileObjectStorage storage, String prefix, String operation) {
        try {
            storage.deletePrefix(prefix);
        } catch (FileStorageException exception) {
            log.warn("{}，prefix={}", operation, prefix, exception);
        }
    }

    private static FileDatasetParsingException invalid(String message) {
        return new FileDatasetParsingException(message);
    }

    public record DiscoveredLayer(
            String sourceKey,
            String sourceName,
            int sourceOrder,
            String layerType,
            boolean previewSupported
    ) {
        public DiscoveredLayer {
            sourceKey = requireText(sourceKey, "GDB 图层 ID 不能为空");
            sourceName = requireText(sourceName, "GDB 图层名称不能为空");
            layerType = requireText(layerType, "GDB 图层类型不能为空");
        }
    }

    private record Manifest(List<ArchiveEntry> entries, long expandedSizeBytes) {
    }

    private record ArchiveEntry(String entryName, String relativePath, long sizeBytes) {
    }

    private record NormalizedEntry(String gdbRoot, String relativePath, boolean ignored) {
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
