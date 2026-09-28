package cn.superhuang.data.scalpel.business.filedataset.service.prepare;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetTemporaryFileManager;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingInfrastructureException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageException;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParsingOptionsResponse;
import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Safely publishes one ZIP containing one Shapefile component set and optional known sidecars. */
@Component
public class FileShapefileArchivePreparationService implements FileDatasetPreparationService {

    private static final Logger log = LoggerFactory.getLogger(FileShapefileArchivePreparationService.class);
    private static final String SHAPEFILE_METADATA_SUFFIX = ".shp.xml";
    private static final Set<String> ALLOWED_AUXILIARY_EXTENSIONS = Set.of(
            "sbn", "sbx", "fbn", "fbx", "ain", "aih", "atx", "ixs", "mxs", "qix", "fix"
    );

    private final ObjectProvider<FileObjectStorage> storageProvider;
    private final FileDatasetTemporaryFileManager temporaryFileManager;
    private final ObjectMapper objectMapper;
    private final int maxEntries;
    private final long maxEntrySize;
    private final long maxExpandedSize;
    private final double maxCompressionRatio;

    public FileShapefileArchivePreparationService(
            ObjectProvider<FileObjectStorage> storageProvider,
            FileDatasetTemporaryFileManager temporaryFileManager,
            ObjectMapper objectMapper,
            @Value("${data-scalpel.file-parsing.shp.max-entries:256}") int maxEntries,
            @Value("${data-scalpel.file-parsing.shp.max-entry-size:1GB}") DataSize maxEntrySize,
            @Value("${data-scalpel.file-parsing.shp.max-expanded-size:4GB}") DataSize maxExpandedSize,
            @Value("${data-scalpel.file-parsing.shp.max-compression-ratio:200}") double maxCompressionRatio
    ) {
        this.storageProvider = storageProvider;
        this.temporaryFileManager = temporaryFileManager;
        this.objectMapper = objectMapper;
        this.maxEntries = maxEntries;
        this.maxEntrySize = maxEntrySize.toBytes();
        this.maxExpandedSize = maxExpandedSize.toBytes();
        this.maxCompressionRatio = maxCompressionRatio;
        if (maxEntries < 1 || this.maxEntrySize < 1 || this.maxExpandedSize < 1 || maxCompressionRatio < 1) {
            throw new IllegalArgumentException("SHP ZIP 安全限制必须大于零");
        }
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.SHP;
    }

    @Override
    public FileDatasetPreparationResult prepare(FileDatasetPreparationInput input) throws IOException {
        if (!supports(input.format())) {
            throw new IllegalArgumentException("SHP 准备器不支持当前文件格式");
        }
        FileObjectStorage storage = requireStorage();
        deletePrefixQuietly(storage, input.materializedPrefix(), "重试 SHP 准备任务前清理旧前缀");
        FileObjectStorage.FileObjectContent raw = storage.open(input.rawObjectKey());
        Path archive = null;
        boolean completed = false;
        try (raw) {
            FileDatasetParsingOptionsResponse.Shp parsingOptions = parsingOptions(input.parsingOptions());
            Charset zipEntryCharset = Charset.forName(parsingOptions.zipEntryCharset());
            archive = temporaryFileManager.materialize(
                    raw.inputStream(), raw.contentLength() >= 0 ? raw.contentLength() : input.rawSizeBytes()
            );
            ArchiveManifest archiveManifest = inspect(archive, zipEntryCharset);
            publishComponents(storage, archive, input.materializedPrefix(), archiveManifest, zipEntryCharset);
            validate(storage, input.materializedPrefix(), archiveManifest, openOptions(parsingOptions));
            byte[] manifestBytes = publishManifest(storage, input.materializedPrefix(), archiveManifest);
            completed = true;
            return new FileDatasetPreparationResult(
                    input.materializedPrefix(),
                    Math.addExact(archiveManifest.expandedSizeBytes(), manifestBytes.length),
                    archiveManifest.components().size() + 1,
                    List.of(new DiscoveredTable(
                            FileDatasetShapefileManifest.CANONICAL_STEM + ".shp",
                            archiveManifest.sourceName(),
                            0
                    ))
            );
        } catch (ZipException exception) {
            deletePrefixQuietly(storage, input.materializedPrefix(), "SHP ZIP 读取失败后的前缀清理");
            String message = exception.getMessage() != null && exception.getMessage().contains("bad entry name")
                    ? "SHP ZIP 内文件名或注释无法按所选编码解码，请检查“SHP ZIP 文件名编码”后重新上传"
                    : "上传内容不是有效的 ZIP 归档，或其中的组件已损坏";
            throw new FileDatasetParsingException(message, exception);
        } catch (RuntimeException | IOException exception) {
            deletePrefixQuietly(storage, input.materializedPrefix(), "SHP 准备失败后的前缀清理");
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
        deletePrefixQuietly(requireStorage(), materializedPrefix, "丢弃过期 SHP 物化结果");
    }

    private ArchiveManifest inspect(Path archive, Charset zipEntryCharset) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile(), zipEntryCharset)) {
            EnumMap<ShapefileComponent, ArchiveComponent> components = new EnumMap<>(ShapefileComponent.class);
            Set<String> normalizedPaths = new HashSet<>();
            List<AuxiliaryEntry> auxiliaryEntries = new ArrayList<>();
            String componentDirectory = null;
            String firstComponentPath = null;
            String normalizedStem = null;
            String sourceName = null;
            long totalExpanded = 0;
            int inspectedEntryCount = 0;
            var enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++inspectedEntryCount > maxEntries) {
                    throw invalid("ZIP 条目数量超过允许上限");
                }
                NormalizedEntry normalized = normalize(entry);
                if (normalized.ignored() || entry.isDirectory()) {
                    continue;
                }
                String duplicatePath = normalized.path().toLowerCase(Locale.ROOT);
                if (!normalizedPaths.add(duplicatePath)) {
                    throw invalid("ZIP 中存在重复条目");
                }
                String auxiliaryStem = auxiliaryStem(normalized.fileName());
                if (auxiliaryStem != null) {
                    auxiliaryEntries.add(new AuxiliaryEntry(
                            normalized.directory(), auxiliaryStem.toLowerCase(Locale.ROOT), normalized.fileName()
                    ));
                    continue;
                }
                ShapefileComponent component = component(normalized.fileName());
                String stem = stem(normalized.fileName());
                if (componentDirectory == null) {
                    componentDirectory = normalized.directory();
                    firstComponentPath = normalized.path();
                    normalizedStem = stem.toLowerCase(Locale.ROOT);
                    sourceName = stem;
                    if (sourceName.length() > 255) {
                        throw invalid("SHP 文件名主体不能超过 255 个字符");
                    }
                } else if (!normalizedStem.equals(stem.toLowerCase(Locale.ROOT))) {
                    throw invalid("ZIP 中发现不同名称的 SHP 组件（" + firstComponentPath + "、"
                            + normalized.path()
                            + "）；请检查是否混入多套数据或组件命名不一致，每个 ZIP 只允许一套同名组件");
                } else if (!componentDirectory.equals(normalized.directory())) {
                    throw invalid("SHP 组件不在同一目录（" + firstComponentPath + "、" + normalized.path()
                            + "）；请将同一套组件放在同一目录后重新打包");
                }
                if (components.containsKey(component)) {
                    throw invalid("ZIP 中存在重复的 SHP 组件");
                }
                long size = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                validateEntry(entry, size, compressedSize, totalExpanded);
                totalExpanded = Math.addExact(totalExpanded, size);
                components.put(component, new ArchiveComponent(entry.getName(), component, size, entry.getCrc()));
            }
            for (ShapefileComponent component : ShapefileComponent.values()) {
                if (component.required() && !components.containsKey(component)) {
                    throw invalid("ZIP 缺少必需的 ." + component.extension() + " 组件");
                }
            }
            if (sourceName == null) {
                throw invalid("ZIP 中没有找到 SHP 组件");
            }
            for (AuxiliaryEntry auxiliary : auxiliaryEntries) {
                if (!componentDirectory.equals(auxiliary.directory())
                        || !normalizedStem.equals(auxiliary.normalizedStem())) {
                    throw invalid("SHP 辅助文件必须与核心组件同目录且同名：" + auxiliary.fileName());
                }
            }
            return new ArchiveManifest(sourceName, Map.copyOf(components), totalExpanded);
        }
    }

    private void validateEntry(ZipEntry entry, long size, long compressedSize, long totalExpanded) {
        if (size < 0 || compressedSize < 0) {
            throw invalid("ZIP 条目缺少可验证的大小信息");
        }
        if (entry.getCrc() < 0) {
            throw invalid("ZIP 条目缺少可验证的 CRC 信息");
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
    }

    private void publishComponents(
            FileObjectStorage storage,
            Path archive,
            String prefix,
            ArchiveManifest manifest,
            Charset zipEntryCharset
    ) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile(), zipEntryCharset)) {
            for (ShapefileComponent kind : ShapefileComponent.values()) {
                ArchiveComponent component = manifest.components().get(kind);
                if (component == null) {
                    continue;
                }
                ZipEntry entry = zip.getEntry(component.entryName());
                if (entry == null) {
                    throw invalid("ZIP 中的 SHP 组件清单在读取时发生变化");
                }
                String objectName = FileDatasetShapefileManifest.CANONICAL_STEM + "." + kind.extension();
                try (VerifiedEntryInputStream inputStream = new VerifiedEntryInputStream(
                        zip.getInputStream(entry), component.sizeBytes(), component.crc(), objectName
                )) {
                    try {
                        storage.store(
                                prefix + "/" + objectName,
                                inputStream,
                                component.sizeBytes(),
                                "application/octet-stream"
                        );
                    } catch (RuntimeException exception) {
                        if (inputStream.readFailure instanceof FileDatasetParsingException parsingException) {
                            throw parsingException;
                        }
                        if (inputStream.readFailure != null) {
                            throw new FileDatasetParsingException(
                                    "SHP ZIP 组件解压失败：" + objectName, inputStream.readFailure
                            );
                        }
                        throw exception;
                    }
                    inputStream.verifyComplete();
                }
            }
        }
    }

    private void validate(
            FileObjectStorage storage,
            String prefix,
            ArchiveManifest manifest,
            ShapefileOpenOptions options
    ) {
        try (ShapefileDataset ignored = storage.openShapefile(prefix, manifest.components().keySet(), options)) {
            // Opening validates all required structures, record counts, DBF encoding and unsupported shape types.
        }
    }

    private byte[] publishManifest(FileObjectStorage storage, String prefix, ArchiveManifest archiveManifest) {
        List<FileDatasetShapefileManifest.ComponentEntry> entries = new ArrayList<>();
        for (ShapefileComponent kind : ShapefileComponent.values()) {
            ArchiveComponent component = archiveManifest.components().get(kind);
            if (component != null) {
                entries.add(new FileDatasetShapefileManifest.ComponentEntry(
                        kind,
                        FileDatasetShapefileManifest.CANONICAL_STEM + "." + kind.extension(),
                        component.sizeBytes()
                ));
            }
        }
        FileDatasetShapefileManifest manifest = new FileDatasetShapefileManifest(
                FileDatasetShapefileManifest.CURRENT_VERSION,
                FileDatasetShapefileManifest.FORMAT,
                archiveManifest.sourceName(),
                entries
        );
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(manifest);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法生成 SHP 组件清单", exception);
        }
        storage.store(
                prefix + "/" + FileDatasetShapefileManifest.OBJECT_NAME,
                new java.io.ByteArrayInputStream(bytes),
                bytes.length,
                "application/json"
        );
        return bytes;
    }

    private FileDatasetParsingOptionsResponse.Shp parsingOptions(String parsingOptions) {
        FileDatasetParsingOptionsResponse options;
        try {
            options = objectMapper.readValue(parsingOptions, FileDatasetParsingOptionsResponse.class);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("已保存的 SHP 解析参数无效", exception);
        }
        if (!(options instanceof FileDatasetParsingOptionsResponse.Shp shp)) {
            throw new FileDatasetParsingException("SHP 解析参数类型不匹配");
        }
        return shp;
    }

    private ShapefileOpenOptions openOptions(FileDatasetParsingOptionsResponse.Shp shp) {
        Charset override = shp.dbfCharsetOverride() == null || shp.dbfCharsetOverride().isBlank()
                ? null : Charset.forName(shp.dbfCharsetOverride());
        return new ShapefileOpenOptions(
                ShapefileReadLimits.defaults(),
                override,
                Charset.forName(shp.dbfFallbackCharset()),
                false
        );
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
        for (String segment : segments) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw invalid("ZIP 条目包含不安全路径");
            }
        }
        boolean ignored = isAllowedMetadata(segments, entry.isDirectory());
        String fileName = segments[segments.length - 1];
        String directory = segments.length == 1
                ? "" : String.join("/", java.util.Arrays.copyOf(segments, segments.length - 1));
        return new NormalizedEntry(path, directory, fileName, ignored);
    }

    private static boolean isAllowedMetadata(String[] segments, boolean directory) {
        if (directory) {
            return true;
        }
        for (String segment : segments) {
            if (segment.equals("__MACOSX") || segment.equals(".DS_Store") || segment.startsWith("._")) {
                return true;
            }
        }
        return false;
    }

    private static String auxiliaryStem(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(SHAPEFILE_METADATA_SUFFIX)
                && normalized.length() > SHAPEFILE_METADATA_SUFFIX.length()) {
            return fileName.substring(0, fileName.length() - SHAPEFILE_METADATA_SUFFIX.length());
        }
        int separator = fileName.lastIndexOf('.');
        if (separator < 1 || separator == fileName.length() - 1) {
            return null;
        }
        String extension = fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_AUXILIARY_EXTENSIONS.contains(extension) ? fileName.substring(0, separator) : null;
    }

    private static ShapefileComponent component(String fileName) {
        int separator = fileName.lastIndexOf('.');
        if (separator < 1 || separator == fileName.length() - 1) {
            throw invalid("ZIP 中只允许包含 .shp/.shx/.dbf 及可选 .cpg/.prj 组件");
        }
        String extension = fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
        for (ShapefileComponent value : ShapefileComponent.values()) {
            if (value.extension().equals(extension)) {
                return value;
            }
        }
        throw invalid("ZIP 中包含不支持的文件：" + fileName);
    }

    private static String stem(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf('.'));
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
            log.warn("中止 SHP 原始 ZIP 读取失败", exception);
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

    private record ArchiveManifest(
            String sourceName,
            Map<ShapefileComponent, ArchiveComponent> components,
            long expandedSizeBytes
    ) {
    }

    private record ArchiveComponent(String entryName, ShapefileComponent kind, long sizeBytes, long crc) {
    }

    /** Confirms the bytes sent to object storage match the ZIP central directory before publishing the manifest. */
    private static final class VerifiedEntryInputStream extends FilterInputStream {

        private final long expectedSize;
        private final long expectedCrc;
        private final String name;
        private final CRC32 crc = new CRC32();
        private long bytesRead;
        private Throwable readFailure;

        private VerifiedEntryInputStream(InputStream input, long expectedSize, long expectedCrc, String name) {
            super(input);
            this.expectedSize = expectedSize;
            this.expectedCrc = expectedCrc;
            this.name = name;
        }

        @Override
        public int read() throws IOException {
            try {
                int value = in.read();
                if (value >= 0) {
                    bytesRead++;
                    crc.update(value);
                    checkSize();
                }
                return value;
            } catch (IOException | FileDatasetParsingException exception) {
                readFailure = exception;
                throw exception;
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            try {
                int count = in.read(buffer, offset, length);
                if (count > 0) {
                    bytesRead += count;
                    crc.update(buffer, offset, count);
                    checkSize();
                }
                return count;
            } catch (IOException | FileDatasetParsingException exception) {
                readFailure = exception;
                throw exception;
            }
        }

        private void checkSize() {
            if (bytesRead > expectedSize) {
                throw invalid("SHP ZIP 组件解压大小与目录声明不一致：" + name);
            }
        }

        private void verifyComplete() throws IOException {
            if (in.read() != -1 || bytesRead != expectedSize) {
                throw invalid("SHP ZIP 组件解压大小与目录声明不一致：" + name);
            }
            if (expectedCrc < 0 || crc.getValue() != expectedCrc) {
                throw invalid("SHP ZIP 组件 CRC 校验失败：" + name);
            }
        }
    }

    private record AuxiliaryEntry(String directory, String normalizedStem, String fileName) {
    }

    private record NormalizedEntry(String path, String directory, String fileName, boolean ignored) {
    }
}
