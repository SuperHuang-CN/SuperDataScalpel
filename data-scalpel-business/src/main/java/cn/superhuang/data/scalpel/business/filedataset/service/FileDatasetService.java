package cn.superhuang.data.scalpel.business.filedataset.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingConfiguration;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParseSource;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParserInputMode;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.SampledContentSizeLimitInputStream;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageObjectNotFoundException;
import cn.superhuang.data.scalpel.business.filedataset.web.request.CreateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.ConfigureFileDatasetParsingRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.FileDatasetParsingOptionsRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.ReplaceFileDatasetContentRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParsingOptionsResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParsingResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetFieldResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetPreviewResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import tools.jackson.databind.ObjectMapper;

@Service
public class FileDatasetService {

    private static final Logger log = LoggerFactory.getLogger(FileDatasetService.class);
    private static final int PARSE_SAMPLE_RECORD_LIMIT = 1_000;

    private final FileDatasetRepository repository;
    private final FileDatasetFieldRepository fieldRepository;
    private final DirectoryService directoryService;
    private final SearchEngine searchEngine;
    private final ObjectProvider<FileObjectStorage> storageProvider;
    private final ObjectMapper objectMapper;
    private final List<FileDatasetParser> parsers;
    private final FileDatasetTemporaryFileManager temporaryFileManager;
    private final TransactionTemplate transactionTemplate;
    private final long maxSampledUncompressedSize;

    public FileDatasetService(
            FileDatasetRepository repository,
            FileDatasetFieldRepository fieldRepository,
            DirectoryService directoryService,
            SearchEngine searchEngine,
            ObjectProvider<FileObjectStorage> storageProvider,
            ObjectMapper objectMapper,
            List<FileDatasetParser> parsers,
            FileDatasetTemporaryFileManager temporaryFileManager,
            PlatformTransactionManager transactionManager,
            @Value("${data-scalpel.file-parsing.max-sampled-uncompressed-size:64MB}") DataSize maxSampledUncompressedSize
    ) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.directoryService = directoryService;
        this.searchEngine = searchEngine;
        this.storageProvider = storageProvider;
        this.objectMapper = objectMapper;
        this.parsers = List.copyOf(parsers);
        this.temporaryFileManager = temporaryFileManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxSampledUncompressedSize = maxSampledUncompressedSize.toBytes();
        if (this.maxSampledUncompressedSize < 1) {
            throw new IllegalArgumentException("文件解析解压后抽样大小上限必须大于零");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<FileDatasetResponse> search(SearchRequest request) {
        Page<FileDataset> result = searchEngine.search(request, FileDataset.class, repository);
        return new PageResponse<>(
                result.getContent().stream().map(FileDatasetResponse::from).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public FileDatasetResponse get(UUID id) {
        return FileDatasetResponse.from(requireDataset(id));
    }

    @Transactional(readOnly = true)
    public FileDatasetParsingResponse parsing(UUID id) {
        return parsingResponse(requireDataset(id));
    }

    public FileDatasetPreviewResponse preview(UUID id, int limit) {
        PreviewPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            FileDataset dataset = requireDataset(id);
            if (dataset.getParseStatus() != cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus.READY) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "请先成功执行文件解析");
            }
            FileDatasetParsingOptionsResponse options = readParsingOptions(dataset.getParsingOptions());
            FileDatasetParser parser = requireParser(dataset.getFormat());
            return new PreviewPreparation(
                    dataset, parser, parserConfiguration(options), parsedFields(dataset.getId())
            );
        }));
        try {
            FileDatasetParser.ParseResult result = parseContent(
                    preparation.dataset(), preparation.parser(), preparation.configuration(), limit
            );
            List<List<Object>> rows = result.rows().stream()
                    .map(row -> preparation.fields().stream().map(field -> row.get(field.name())).toList())
                    .toList();
            return new FileDatasetPreviewResponse(preparation.fields(), rows, limit, result.truncated());
        } catch (FileStorageObjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件内容不存在", exception);
        } catch (FileStorageException exception) {
            throw storageUnavailable(exception);
        } catch (FileDatasetParsingException | IOException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "无法按已保存的配置预览文件：" + safeParseError(exception), exception);
        }
    }

    public FileDatasetResponse create(CreateFileDatasetRequest request, MultipartFile file) {
        directoryService.validateAssignment(DirectoryScope.FILE_DATASET, request.directoryId());
        UploadedFile uploadedFile = validateUpload(request.format(), file);
        FileObjectStorage storage = requireStorage();
        String objectKey = newObjectKey(uploadedFile.fileName());
        FileObjectStorage.StoredFileObject storedObject = store(storage, objectKey, file, uploadedFile);

        try {
            return requireTransactionResult(transactionTemplate.execute(status -> {
                FileDataset dataset = FileDataset.create(
                        request.directoryId(), request.name(), request.format(), uploadedFile.compression(), uploadedFile.fileName(),
                        objectKey, uploadedFile.contentType(), uploadedFile.sizeBytes(), storedObject.eTag(), request.description()
                );
                return FileDatasetResponse.from(repository.saveAndFlush(dataset));
            }));
        } catch (RuntimeException exception) {
            deleteQuietly(storage, objectKey, "创建文件数据集失败后的补偿删除");
            throw exception;
        }
    }

    @Transactional
    public FileDatasetResponse update(UUID id, UpdateFileDatasetRequest request) {
        FileDataset dataset = requireDataset(id);
        directoryService.validateAssignment(DirectoryScope.FILE_DATASET, request.directoryId());
        validateFormatMatchesFile(request.format(), dataset.getOriginalFileName(), dataset.getCompression());
        boolean formatChanged = dataset.getFormat() != request.format();
        dataset.update(request.directoryId(), request.name(), request.format(), request.description());
        if (formatChanged) {
            clearParsedFields(dataset.getId());
        }
        return FileDatasetResponse.from(repository.saveAndFlush(dataset));
    }

    @Transactional
    public FileDatasetParsingResponse configureParsing(UUID id, ConfigureFileDatasetParsingRequest request) {
        FileDataset dataset = requireDataset(id);
        FileDatasetParsingOptionsRequest options = request.options();
        validateParsingOptions(dataset.getFormat(), options);
        FileDatasetParsingOptionsResponse responseOptions = FileDatasetParsingOptionsResponse.from(options);
        dataset.configureParsing(writeParsingOptions(responseOptions));
        clearParsedFields(dataset.getId());
        repository.saveAndFlush(dataset);
        return parsingResponse(dataset);
    }

    public FileDatasetParsingResponse parse(UUID id) {
        ParsePreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareParsing(id))
        );
        try {
            FileDatasetParser.ParseResult result = parseContent(
                    preparation.dataset(), preparation.parser(), preparation.configuration(), PARSE_SAMPLE_RECORD_LIMIT
            );
            if (result.fields().isEmpty()) {
                throw new FileDatasetParsingException("未识别到可用字段");
            }
            return requireTransactionResult(transactionTemplate.execute(
                    status -> completeParsing(id, result)
            ));
        } catch (FileStorageObjectNotFoundException exception) {
            return failParsing(id, "文件内容不存在");
        } catch (FileStorageException exception) {
            failParsing(id, safeParseError(exception));
            throw storageUnavailable(exception);
        } catch (FileDatasetParsingException | IOException exception) {
            return failParsing(id, safeParseError(exception));
        }
    }

    private ParsePreparation prepareParsing(UUID id) {
        FileDataset dataset = requireDataset(id);
        FileDatasetParsingOptionsResponse options = dataset.hasParsingOptions()
                ? readParsingOptions(dataset.getParsingOptions()) : null;
        if (options == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先保存解析参数");
        }
        FileDatasetParser parser = requireParser(dataset.getFormat());
        FileDatasetParsingConfiguration configuration = parserConfiguration(options);
        dataset.startParsing();
        clearParsedFields(dataset.getId());
        repository.saveAndFlush(dataset);
        return new ParsePreparation(dataset, parser, configuration);
    }

    private FileDatasetParsingResponse completeParsing(UUID id, FileDatasetParser.ParseResult result) {
        FileDataset dataset = requireDataset(id);
        persistParsedFields(dataset.getId(), result.fields());
        dataset.completeParsing(writeParsedMetadata(result));
        repository.saveAndFlush(dataset);
        return parsingResponse(dataset);
    }

    private FileDatasetParsingResponse failParsing(UUID id, String reason) {
        return requireTransactionResult(transactionTemplate.execute(status -> {
            FileDataset dataset = requireDataset(id);
            dataset.failParsing(reason.length() > 2_000 ? reason.substring(0, 2_000) : reason);
            repository.saveAndFlush(dataset);
            return parsingResponse(dataset);
        }));
    }

    public FileDatasetResponse replaceContent(
            UUID id,
            ReplaceFileDatasetContentRequest request,
            MultipartFile file
    ) {
        transactionTemplate.executeWithoutResult(status -> requireDataset(id));
        UploadedFile uploadedFile = validateUpload(request.format(), file);
        FileObjectStorage storage = requireStorage();
        String newObjectKey = newObjectKey(uploadedFile.fileName());
        FileObjectStorage.StoredFileObject storedObject = store(storage, newObjectKey, file, uploadedFile);

        try {
            return requireTransactionResult(transactionTemplate.execute(status -> {
                FileDataset dataset = requireDataset(id);
                String oldObjectKey = dataset.getObjectKey();
                dataset.replaceContent(
                        request.format(), uploadedFile.compression(), uploadedFile.fileName(), newObjectKey,
                        uploadedFile.contentType(), uploadedFile.sizeBytes(), storedObject.eTag()
                );
                clearParsedFields(dataset.getId());
                FileDatasetResponse response = FileDatasetResponse.from(repository.saveAndFlush(dataset));
                deleteAfterCommit(storage, oldObjectKey, "替换文件内容后的旧对象清理");
                return response;
            }));
        } catch (RuntimeException exception) {
            deleteQuietly(storage, newObjectKey, "替换文件内容失败后的补偿删除");
            throw exception;
        }
    }

    public FileDatasetContent openContent(UUID id) {
        ContentPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            FileDataset dataset = requireDataset(id);
            return new ContentPreparation(
                    dataset.getOriginalFileName(), dataset.getObjectKey(), dataset.getContentType(), dataset.getSizeBytes()
            );
        }));
        try {
            FileObjectStorage.FileObjectContent content = requireStorage().open(preparation.objectKey());
            String contentType = hasText(content.contentType()) ? content.contentType() : preparation.contentType();
            long contentLength = content.contentLength() >= 0 ? content.contentLength() : preparation.sizeBytes();
            return new FileDatasetContent(preparation.fileName(), contentType, contentLength, content.inputStream());
        } catch (FileStorageObjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件内容不存在", exception);
        } catch (FileStorageException exception) {
            throw storageUnavailable(exception);
        }
    }

    @Transactional
    public void delete(UUID id) {
        FileDataset dataset = requireDataset(id);
        FileObjectStorage storage = requireStorage();
        String objectKey = dataset.getObjectKey();
        clearParsedFields(dataset.getId());
        repository.delete(dataset);
        repository.flush();
        deleteAfterCommit(storage, objectKey, "删除文件数据集后的对象清理");
    }

    private FileDataset requireDataset(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集不存在"));
    }

    private FileDatasetParsingResponse parsingResponse(FileDataset dataset) {
        FileDatasetParsingOptionsResponse options = dataset.hasParsingOptions()
                ? readParsingOptions(dataset.getParsingOptions()) : null;
        FileDatasetParsedMetadata metadata = readParsedMetadata(dataset.getParsedMetadata());
        List<FileDatasetFieldResponse> fields = dataset.getParseStatus()
                == cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus.READY
                ? parsedFields(dataset.getId()) : List.of();
        return new FileDatasetParsingResponse(
                dataset.getId(), dataset.getFormat(), dataset.getCompression(), dataset.getParseStatus(), options != null, options,
                dataset.getParseError(), metadata.sampledRecordCount(), metadata.truncated(), fields
        );
    }

    private List<FileDatasetFieldResponse> parsedFields(UUID datasetId) {
        return FileDatasetFieldResponse.from(fieldRepository.findByFileDatasetIdOrderBySortOrderAsc(datasetId));
    }

    private FileDatasetParser requireParser(FileDatasetFormat format) {
        return parsers.stream().filter(parser -> parser.supports(format)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持 " + format + " 格式解析"));
    }

    private FileDatasetParsingConfiguration parserConfiguration(FileDatasetParsingOptionsResponse options) {
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
            case FileDatasetParsingOptionsResponse.Spreadsheet value -> new FileDatasetParsingConfiguration.Spreadsheet(
                    value.sheetName(), value.headerRowIndex(), value.dataStartRowIndex()
            );
            case FileDatasetParsingOptionsResponse.Parquet ignored -> new FileDatasetParsingConfiguration.Parquet();
            case FileDatasetParsingOptionsResponse.Avro ignored -> new FileDatasetParsingConfiguration.Avro();
            case FileDatasetParsingOptionsResponse.Shapefile ignored ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持 SHP 格式解析");
            case FileDatasetParsingOptionsResponse.FileGdb ignored ->
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持 GDB 格式解析");
        };
    }

    private void persistParsedFields(UUID datasetId, List<FileDatasetParser.Field> fields) {
        List<FileDatasetField> entities = fields.stream().map(field -> FileDatasetField.create(
                datasetId, field.name(), field.sortOrder(), field.logicalType(), field.nullable()
        )).toList();
        fieldRepository.saveAllAndFlush(entities);
    }

    private void clearParsedFields(UUID datasetId) {
        fieldRepository.deleteByFileDatasetId(datasetId);
        fieldRepository.flush();
    }

    private FileDatasetParser.ParseResult parseContent(
            FileDataset dataset,
            FileDatasetParser parser,
            FileDatasetParsingConfiguration configuration,
            int recordLimit
    ) throws IOException {
        FileObjectStorage.FileObjectContent content = requireStorage().open(dataset.getObjectKey());
        boolean completed = false;
        try (content) {
            InputStream inputStream = content.inputStream();
            if (parser.inputMode() == FileDatasetParserInputMode.STREAM) {
                InputStream parsedInput = parsedInputStream(dataset, inputStream);
                FileDatasetParser.ParseResult result = parser.parse(
                        new FileDatasetParseSource.Stream(parsedInput), configuration, recordLimit
                );
                if (result.truncated()) {
                    abortContent(content);
                }
                completed = true;
                return result;
            }
            Path localFile = temporaryFileManager.materialize(
                    inputStream, content.contentLength() >= 0 ? content.contentLength() : dataset.getSizeBytes()
            );
            try {
                FileDatasetParser.ParseResult result = parser.parse(
                        new FileDatasetParseSource.LocalFile(localFile), configuration, recordLimit
                );
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

    private InputStream parsedInputStream(FileDataset dataset, InputStream inputStream) throws IOException {
        InputStream decoded = dataset.getCompression() == FileDatasetCompression.GZIP
                ? new GZIPInputStream(inputStream) : inputStream;
        return new SampledContentSizeLimitInputStream(decoded, maxSampledUncompressedSize);
    }

    private void abortContent(FileObjectStorage.FileObjectContent content) {
        try {
            content.abort();
        } catch (RuntimeException exception) {
            log.warn("中止文件对象读取失败", exception);
        }
    }

    private void validateParsingOptions(FileDatasetFormat format, FileDatasetParsingOptionsRequest options) {
        boolean compatible = switch (format) {
            case CSV, TSV -> options instanceof FileDatasetParsingOptionsRequest.Csv;
            case TXT -> options instanceof FileDatasetParsingOptionsRequest.Text;
            case JSON -> options instanceof FileDatasetParsingOptionsRequest.Json;
            case JSONL -> options instanceof FileDatasetParsingOptionsRequest.JsonLines;
            case XLS, XLSX -> options instanceof FileDatasetParsingOptionsRequest.Spreadsheet;
            case PARQUET -> options instanceof FileDatasetParsingOptionsRequest.Parquet;
            case AVRO -> options instanceof FileDatasetParsingOptionsRequest.Avro;
            case SHP -> options instanceof FileDatasetParsingOptionsRequest.Shapefile;
            case GDB -> options instanceof FileDatasetParsingOptionsRequest.FileGdb;
            case OTHER -> false;
        };
        if (!compatible) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "解析参数类型与文件格式不匹配");
        }

        switch (options) {
            case FileDatasetParsingOptionsRequest.Csv value -> {
                validateCharset(value.charset());
                if (format == FileDatasetFormat.TSV && !"\t".equals(value.fieldDelimiter())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TSV 文件的字段分隔符必须是制表符");
                }
            }
            case FileDatasetParsingOptionsRequest.Text value -> validateCharset(value.charset());
            case FileDatasetParsingOptionsRequest.Json value -> {
                validateCharset(value.charset());
                if (hasText(value.rootPointer()) && !value.rootPointer().trim().startsWith("/")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON 根路径必须使用以 / 开头的 JSON Pointer");
                }
            }
            case FileDatasetParsingOptionsRequest.JsonLines value -> validateCharset(value.charset());
            case FileDatasetParsingOptionsRequest.Spreadsheet value -> {
                if (value.dataStartRowIndex() <= value.headerRowIndex()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据起始行必须位于表头行之后");
                }
            }
            case FileDatasetParsingOptionsRequest.Shapefile value -> validateCharset(value.charset());
            case FileDatasetParsingOptionsRequest.Parquet ignored -> {
            }
            case FileDatasetParsingOptionsRequest.Avro ignored -> {
            }
            case FileDatasetParsingOptionsRequest.FileGdb ignored -> {
            }
        }
    }

    private void validateCharset(String charset) {
        try {
            if (!Charset.isSupported(charset.trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件编码：" + charset);
            }
        } catch (IllegalCharsetNameException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件编码：" + charset, exception);
        }
    }

    private String writeParsingOptions(FileDatasetParsingOptionsResponse options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存文件解析参数", exception);
        }
    }

    private FileDatasetParsingOptionsResponse readParsingOptions(String value) {
        try {
            return objectMapper.readValue(value, FileDatasetParsingOptionsResponse.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("已保存的文件解析参数无效", exception);
        }
    }

    private String writeParsedMetadata(FileDatasetParser.ParseResult result) {
        try {
            return objectMapper.writeValueAsString(new FileDatasetParsedMetadata(result.rows().size(), result.truncated()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存文件解析元数据", exception);
        }
    }

    private FileDatasetParsedMetadata readParsedMetadata(String value) {
        if (!hasText(value)) {
            return new FileDatasetParsedMetadata(0, false);
        }
        try {
            return objectMapper.readValue(value, FileDatasetParsedMetadata.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("已保存的文件解析元数据无效", exception);
        }
    }

    private static String safeParseError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "文件内容或解析参数无效" : message;
    }

    private FileObjectStorage requireStorage() {
        FileObjectStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件对象存储尚未配置");
        }
        return storage;
    }

    private FileObjectStorage.StoredFileObject store(
            FileObjectStorage storage,
            String objectKey,
            MultipartFile file,
            UploadedFile uploadedFile
    ) {
        try (InputStream inputStream = file.getInputStream()) {
            return storage.store(objectKey, inputStream, uploadedFile.sizeBytes(), uploadedFile.contentType());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取上传文件", exception);
        } catch (FileStorageException exception) {
            throw storageUnavailable(exception);
        }
    }

    private UploadedFile validateUpload(FileDatasetFormat format, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        FileDatasetCompression compression = compressionOf(fileName);
        validateFormatMatchesFile(format, fileName, compression);
        validateContentSignature(format, compression, file);
        String contentType = compression == FileDatasetCompression.GZIP
                ? "application/gzip" : normalizeContentType(file.getContentType());
        return new UploadedFile(fileName, contentType, file.getSize(), compression);
    }

    private void validateFormatMatchesFile(
            FileDatasetFormat format,
            String fileName,
            FileDatasetCompression compression
    ) {
        String extension = extensionOf(baseFileName(fileName, compression));
        Set<String> allowedExtensions = switch (format) {
            case CSV -> Set.of("csv");
            case TSV -> Set.of("tsv");
            case TXT -> Set.of("txt");
            case JSON -> Set.of("json");
            case JSONL -> Set.of("jsonl", "ndjson");
            case XLS -> Set.of("xls");
            case XLSX -> Set.of("xlsx");
            case PARQUET -> Set.of("parquet");
            case AVRO -> Set.of("avro");
            case SHP, GDB -> Set.of("zip");
            case OTHER -> Set.of();
        };
        if (!allowedExtensions.isEmpty() && !allowedExtensions.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, format + " 格式的文件扩展名不匹配");
        }
        if (compression == FileDatasetCompression.GZIP && !supportsGzip(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, format + " 格式暂不支持 GZIP 外层压缩");
        }
    }

    private void validateContentSignature(FileDatasetFormat format, FileDatasetCompression compression, MultipartFile file) {
        if (compression == FileDatasetCompression.GZIP && !hasSignature(file, 0x1F, 0x8B)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件扩展名为 GZIP，但内容不是有效 GZIP");
        }
        if (compression == FileDatasetCompression.NONE && hasSignature(file, 0x1F, 0x8B)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "检测到 GZIP 内容，请将文件名补全为 .gz");
        }
        if (format == FileDatasetFormat.AVRO && !hasSignature(file, 'O', 'b', 'j', 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AVRO 文件内容不是有效的 Object Container File");
        }
    }

    private boolean hasSignature(MultipartFile file, int... signature) {
        try (InputStream inputStream = new BufferedInputStream(file.getInputStream())) {
            for (int expected : signature) {
                if (inputStream.read() != expected) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取上传文件", exception);
        }
    }

    private static FileDatasetCompression compressionOf(String fileName) {
        return "gz".equals(extensionOf(fileName)) ? FileDatasetCompression.GZIP : FileDatasetCompression.NONE;
    }

    private static String baseFileName(String fileName, FileDatasetCompression compression) {
        if (compression != FileDatasetCompression.GZIP) {
            return fileName;
        }
        int extensionStart = fileName.lastIndexOf('.');
        if (extensionStart < 1) {
            return fileName;
        }
        String baseFileName = fileName.substring(0, extensionStart);
        if (extensionOf(baseFileName).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GZIP 文件名必须包含原始文件扩展名");
        }
        return baseFileName;
    }

    private static boolean supportsGzip(FileDatasetFormat format) {
        return switch (format) {
            case CSV, TSV, TXT, JSONL -> true;
            case JSON, XLS, XLSX, PARQUET, AVRO, SHP, GDB, OTHER -> false;
        };
    }

    private String newObjectKey(String fileName) {
        String extension = extensionOf(fileName);
        String suffix = extension.matches("[a-z0-9]{1,16}") ? "." + extension : "";
        return "file-datasets/" + UUID.randomUUID() + suffix;
    }

    private void deleteAfterCommit(FileObjectStorage storage, String objectKey, String operation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(storage, objectKey, operation);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(storage, objectKey, operation);
            }
        });
    }

    private void deleteQuietly(FileObjectStorage storage, String objectKey, String operation) {
        try {
            storage.delete(objectKey);
        } catch (FileStorageException exception) {
            log.warn("{}失败，objectKey={}", operation, objectKey, exception);
        }
    }

    private ResponseStatusException storageUnavailable(FileStorageException exception) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件对象存储不可用", exception);
    }

    private static String safeFileName(String originalFileName) {
        String value = originalFileName == null ? "" : originalFileName.trim().replace('\\', '/');
        int lastSlash = value.lastIndexOf('/');
        String fileName = lastSlash >= 0 ? value.substring(lastSlash + 1) : value;
        if (fileName.isBlank() || fileName.indexOf('\0') >= 0 || fileName.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件名无效");
        }
        return fileName;
    }

    private static String normalizeContentType(String value) {
        if (!hasText(value)) {
            return "application/octet-stream";
        }
        if (value.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件 Content-Type 过长");
        }
        return value.trim();
    }

    private static String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 1 || index == fileName.length() - 1 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("事务未返回文件数据集处理结果");
        }
        return value;
    }

    private record PreviewPreparation(
            FileDataset dataset,
            FileDatasetParser parser,
            FileDatasetParsingConfiguration configuration,
            List<FileDatasetFieldResponse> fields
    ) {
    }

    private record ParsePreparation(
            FileDataset dataset,
            FileDatasetParser parser,
            FileDatasetParsingConfiguration configuration
    ) {
    }

    private record ContentPreparation(String fileName, String objectKey, String contentType, long sizeBytes) {
    }

    private record UploadedFile(String fileName, String contentType, long sizeBytes, FileDatasetCompression compression) {
    }
}
