package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.service.FileDatasetTemporaryFileManager;
import cn.superhuang.data.scalpel.business.filedataset.service.prepare.FileDatasetShapefileManifest;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageException;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParsingOptionsResponse;
import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
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
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** Shared transaction-free parsing path used by synchronous APIs, previews, and queue workers. */
@Component
public class FileDatasetContentParser {

    private static final Logger log = LoggerFactory.getLogger(FileDatasetContentParser.class);

    private final ObjectProvider<FileObjectStorage> storageProvider;
    private final ObjectMapper objectMapper;
    private final List<FileDatasetParser> parsers;
    private final FileDatasetTemporaryFileManager temporaryFileManager;
    private final long maxSampledUncompressedSize;
    private final long maxValidatedUncompressedSize;
    private final ShapefileReadLimits shapefilePreviewReadLimits;

    public FileDatasetContentParser(
            ObjectProvider<FileObjectStorage> storageProvider,
            ObjectMapper objectMapper,
            List<FileDatasetParser> parsers,
            FileDatasetTemporaryFileManager temporaryFileManager,
            @Value("${data-scalpel.file-parsing.max-sampled-uncompressed-size:64MB}")
            DataSize maxSampledUncompressedSize,
            @Value("${data-scalpel.file-parsing.max-validated-uncompressed-size:2GB}")
            DataSize maxValidatedUncompressedSize,
            @Value("${data-scalpel.file-parsing.shp.max-preview-geometry-points-per-feature:100000}")
            int maxShapefilePreviewGeometryPointsPerFeature
    ) {
        this.storageProvider = storageProvider;
        this.objectMapper = objectMapper;
        this.parsers = List.copyOf(parsers);
        this.temporaryFileManager = temporaryFileManager;
        this.maxSampledUncompressedSize = maxSampledUncompressedSize.toBytes();
        this.maxValidatedUncompressedSize = maxValidatedUncompressedSize.toBytes();
        if (this.maxSampledUncompressedSize < 1) {
            throw new IllegalArgumentException("文件解析解压后抽样大小上限必须大于零");
        }
        if (this.maxValidatedUncompressedSize < this.maxSampledUncompressedSize) {
            throw new IllegalArgumentException("文件完整校验解压大小上限不能小于抽样大小上限");
        }
        ShapefileReadLimits defaults = ShapefileReadLimits.defaults();
        this.shapefilePreviewReadLimits = new ShapefileReadLimits(
                defaults.maxComponentFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxMetadataBytes(),
                defaults.maxParts(),
                maxShapefilePreviewGeometryPointsPerFeature,
                defaults.maxIndexRecords(),
                defaults.maxFeaturesPerCursor()
        );
    }

    public FileDatasetParser.ParseResult parse(Input input, int recordLimit) throws IOException {
        return parseInternal(input, recordLimit, false);
    }

    public FileDatasetParser.ParseResult validate(Input input, int previewLimit) throws IOException {
        return parseInternal(input, previewLimit, true);
    }

    private FileDatasetParser.ParseResult parseInternal(Input input, int recordLimit, boolean fullValidation)
            throws IOException {
        if (recordLimit < 1) {
            throw new IllegalArgumentException("文件解析抽样行数必须大于零");
        }
        FileDatasetParser parser = requireParser(input.format());
        FileDatasetParsingConfiguration configuration = configuration(
                readParsingOptions(input.parsingOptions()), input.sourceKey(), input.epsgCodeOverride()
        );
        FileObjectStorage storage = requireStorage();
        if (parser.inputMode() == FileDatasetParserInputMode.FILE_GDB) {
            try (FileGeodatabase database = storage.openFileGeodatabase(input.objectKey())) {
                return fullValidation
                        ? parser.validate(new FileDatasetParseSource.FileGdb(database), configuration, recordLimit)
                        : parser.parse(new FileDatasetParseSource.FileGdb(database), configuration, recordLimit);
            }
        }
        if (parser.inputMode() == FileDatasetParserInputMode.SHAPEFILE_COMPONENT_SET) {
            FileDatasetShapefileManifest manifest = readShapefileManifest(storage, input.objectKey());
            FileDatasetParsingConfiguration.Shp shp = requireShapefileConfiguration(configuration);
            Charset override = shp.dbfCharsetOverride() == null
                    ? null : Charset.forName(shp.dbfCharsetOverride());
            ShapefileOpenOptions options = new ShapefileOpenOptions(
                    fullValidation ? ShapefileReadLimits.defaults() : shapefilePreviewReadLimits,
                    override,
                    Charset.forName(shp.dbfFallbackCharset()),
                    false
            );
            try (ShapefileDataset dataset = storage.openShapefile(
                    input.objectKey(), manifest.componentKinds(), options
            )) {
                return fullValidation
                        ? parser.validate(new FileDatasetParseSource.Shapefile(dataset), configuration, recordLimit)
                        : parser.parse(new FileDatasetParseSource.Shapefile(dataset), configuration, recordLimit);
            }
        }
        FileObjectStorage.FileObjectContent content = storage.open(input.objectKey());
        boolean completed = false;
        try (content) {
            InputStream storageInput = new StorageReadInputStream(content.inputStream());
            if (parser.inputMode() == FileDatasetParserInputMode.STREAM) {
                InputStream parsedInput = parsedInputStream(input.compression(), storageInput, fullValidation);
                FileDatasetParser.ParseResult result = fullValidation
                        ? parser.validate(new FileDatasetParseSource.Stream(parsedInput), configuration, recordLimit)
                        : parser.parse(new FileDatasetParseSource.Stream(parsedInput), configuration, recordLimit);
                if (result.truncated()) {
                    abortContent(content);
                }
                completed = true;
                return result;
            }
            Path localFile = temporaryFileManager.materialize(
                    new SampledContentSizeLimitInputStream(
                            storageInput,
                            materializedSizeLimit(input.format(), fullValidation)
                    ),
                    content.contentLength() >= 0 ? content.contentLength() : input.sizeBytes()
            );
            try {
                if (input.format() == FileDatasetFormat.GEOPARQUET) {
                    // Parquet may be internally compressed. Its physical object size is not a safe
                    // proxy for the amount of data the full Geometry scan will decompress.
                    GeoParquetFileDatasetParser.requireUncompressedSizeWithin(
                            localFile, maxValidatedUncompressedSize
                    );
                }
                FileDatasetParser.ParseResult result = fullValidation
                        ? parser.validate(new FileDatasetParseSource.LocalFile(localFile), configuration, recordLimit)
                        : parser.parse(new FileDatasetParseSource.LocalFile(localFile), configuration, recordLimit);
                completed = true;
                return result;
            } finally {
                temporaryFileManager.delete(localFile);
            }
        } finally {
            if (!completed) {
                abortContent(content);
            }
        }
    }

    private long materializedSizeLimit(FileDatasetFormat format, boolean fullValidation) {
        // Seekable spatial containers remain previewable after import even when their physical
        // object exceeds the bounded stream-preview limit. GeoParquet separately checks Footer
        // declared uncompressed size after materialization; GeoPackage is bounded by the same
        // temporary-file and full-validation limits.
        return fullValidation || format == FileDatasetFormat.GEOPARQUET || format == FileDatasetFormat.GPKG
                ? maxValidatedUncompressedSize : maxSampledUncompressedSize;
    }

    private FileDatasetParser requireParser(FileDatasetFormat format) {
        return parsers.stream().filter(parser -> parser.supports(format)).findFirst()
                .orElseThrow(() -> new FileDatasetParsingException("暂不支持 " + format + " 格式解析"));
    }

    private FileDatasetParsingOptionsResponse readParsingOptions(String value) {
        try {
            return objectMapper.readValue(value, FileDatasetParsingOptionsResponse.class);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("已保存的文件解析参数无效", exception);
        }
    }

    private static FileDatasetParsingConfiguration configuration(
            FileDatasetParsingOptionsResponse options,
            String sourceKey,
            Integer epsgCodeOverride
    ) {
        return switch (options) {
            case FileDatasetParsingOptionsResponse.Csv value -> new FileDatasetParsingConfiguration.Csv(
                    value.charset(), value.fieldDelimiter(), value.recordDelimiter(), value.quoteCharacter(),
                    value.escapeCharacter(), value.firstRowHeader()
            );
            case FileDatasetParsingOptionsResponse.Text value -> new FileDatasetParsingConfiguration.Text(
                    value.charset(), value.recordDelimiter()
            );
            case FileDatasetParsingOptionsResponse.Json value -> new FileDatasetParsingConfiguration.Json(
                    value.charset(), value.rootPointer()
            );
            case FileDatasetParsingOptionsResponse.JsonLines value -> new FileDatasetParsingConfiguration.JsonLines(
                    value.charset(), value.recordDelimiter()
            );
            case FileDatasetParsingOptionsResponse.GeoJson value ->
                    new FileDatasetParsingConfiguration.GeoJson(value.epsgCode());
            case FileDatasetParsingOptionsResponse.GeoJsonLines value ->
                    new FileDatasetParsingConfiguration.GeoJsonLines(value.epsgCode());
            case FileDatasetParsingOptionsResponse.GeoParquet ignored ->
                    new FileDatasetParsingConfiguration.GeoParquet();
            case FileDatasetParsingOptionsResponse.GeoPackage ignored ->
                    new FileDatasetParsingConfiguration.GeoPackage(sourceKey);
            case FileDatasetParsingOptionsResponse.Spreadsheet value ->
                    new FileDatasetParsingConfiguration.Spreadsheet(
                            sourceKey, value.headerRowIndex(), value.dataStartRowIndex()
                    );
            case FileDatasetParsingOptionsResponse.Parquet ignored -> new FileDatasetParsingConfiguration.Parquet();
            case FileDatasetParsingOptionsResponse.Avro ignored -> new FileDatasetParsingConfiguration.Avro();
            case FileDatasetParsingOptionsResponse.Gdb value ->
                    new FileDatasetParsingConfiguration.Gdb(
                            sourceKey, epsgCodeOverride == null ? value.epsgCode() : epsgCodeOverride
                    );
            case FileDatasetParsingOptionsResponse.Shp value -> new FileDatasetParsingConfiguration.Shp(
                    value.dbfCharsetOverride(), value.dbfFallbackCharset(),
                    epsgCodeOverride == null ? value.epsgCode() : epsgCodeOverride
            );
        };
    }

    private FileDatasetShapefileManifest readShapefileManifest(FileObjectStorage storage, String prefix) {
        FileObjectStorage.FileObjectContent content = storage.open(
                prefix + "/" + FileDatasetShapefileManifest.OBJECT_NAME
        );
        try (content) {
            if (content.contentLength() > 1024 * 1024) {
                throw new FileDatasetParsingException("SHP 组件清单超过允许大小");
            }
            return objectMapper.readValue(content.inputStream(), FileDatasetShapefileManifest.class);
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof FileDatasetParsingException parsingException) {
                throw parsingException;
            }
            throw new FileDatasetParsingException("SHP 组件清单无效", exception);
        }
    }

    private static FileDatasetParsingConfiguration.Shp requireShapefileConfiguration(
            FileDatasetParsingConfiguration configuration
    ) {
        if (configuration instanceof FileDatasetParsingConfiguration.Shp shp) {
            return shp;
        }
        throw new FileDatasetParsingException("SHP 解析参数类型不匹配");
    }

    private InputStream parsedInputStream(
            FileDatasetCompression compression,
            InputStream inputStream,
            boolean fullValidation
    )
            throws IOException {
        if (compression == FileDatasetCompression.ZIP) {
            throw new FileDatasetParsingException("ZIP 归档必须先完成目录物化后才能解析");
        }
        InputStream decoded = compression == FileDatasetCompression.GZIP
                ? new GZIPInputStream(inputStream) : inputStream;
        return new SampledContentSizeLimitInputStream(
                decoded,
                fullValidation ? maxValidatedUncompressedSize : maxSampledUncompressedSize
        );
    }

    private FileObjectStorage requireStorage() {
        FileObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new FileDatasetParsingInfrastructureException("文件对象存储尚未配置");
        }
        return storage;
    }

    private static void abortContent(FileObjectStorage.FileObjectContent content) {
        try {
            content.abort();
        } catch (RuntimeException exception) {
            log.warn("中止文件对象读取失败", exception);
        }
    }

    public record Input(
            FileDatasetFormat format,
            FileDatasetCompression compression,
            String objectKey,
            long sizeBytes,
            String parsingOptions,
            String sourceKey,
            Integer epsgCodeOverride
    ) {
        public Input(
                FileDatasetFormat format,
                FileDatasetCompression compression,
                String objectKey,
                long sizeBytes,
                String parsingOptions,
                String sourceKey
        ) {
            this(format, compression, objectKey, sizeBytes, parsingOptions, sourceKey, null);
        }

        public Input {
            Objects.requireNonNull(format, "文件格式不能为空");
            Objects.requireNonNull(compression, "文件压缩方式不能为空");
            objectKey = requireText(objectKey, "对象存储 Key 不能为空");
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("文件大小不能小于零");
            }
            parsingOptions = requireText(parsingOptions, "解析参数不能为空");
            sourceKey = requireText(sourceKey, "来源键不能为空");
        }

        private static String requireText(String value, String message) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(message);
            }
            return value.trim();
        }
    }

    /** Distinguishes remote stream interruptions from malformed compressed or parser content. */
    private static final class StorageReadInputStream extends FilterInputStream {

        private StorageReadInputStream(InputStream inputStream) {
            super(inputStream);
        }

        @Override
        public int read() {
            try {
                return super.read();
            } catch (IOException exception) {
                throw storageReadFailure(exception);
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            try {
                return super.read(bytes, offset, length);
            } catch (IOException exception) {
                throw storageReadFailure(exception);
            }
        }

        @Override
        public long skip(long count) {
            try {
                return super.skip(count);
            } catch (IOException exception) {
                throw storageReadFailure(exception);
            }
        }

        private static FileStorageException storageReadFailure(IOException exception) {
            return new FileStorageException("文件对象读取中断", exception);
        }
    }
}
