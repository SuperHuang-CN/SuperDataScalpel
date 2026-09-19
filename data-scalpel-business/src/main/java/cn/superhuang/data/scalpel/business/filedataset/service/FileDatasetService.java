package cn.superhuang.data.scalpel.business.filedataset.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJob;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetStorageKind;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSource;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSourceLoadMode;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableSourceRepository;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetContentParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParseSource;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingInfrastructureException;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetSchemaValidator;
import cn.superhuang.data.scalpel.business.filedataset.service.queue.FileDatasetParseJobSubmissionService;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageObjectNotFoundException;
import cn.superhuang.data.scalpel.business.filedataset.web.request.CreateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.FileDatasetParsingOptionsRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetTableRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.request.UpdateFileDatasetTableSpatialReferenceRequest;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetFieldResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetCanvasMetadataResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetCanvasTableMetadataResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetFileResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParsingOptionsResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetPreviewResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetSchemaResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableLoadSubmissionResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetTableSourceResponse;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetUploadResponse;
import cn.superhuang.data.scalpel.business.task.service.CanvasFileDatasetReferenceService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class FileDatasetService {

    private static final Logger log = LoggerFactory.getLogger(FileDatasetService.class);
    private static final String SINGLE_TABLE_SOURCE_KEY = "FILE";
    private static final Set<FileDatasetParseJobStatus> NON_TERMINAL_JOB_STATUSES =
            Set.of(FileDatasetParseJobStatus.QUEUED, FileDatasetParseJobStatus.RUNNING);

    private final FileDatasetRepository repository;
    private final FileDatasetFileRepository fileRepository;
    private final FileDatasetTableRepository tableRepository;
    private final FileDatasetTableSourceRepository sourceRepository;
    private final FileDatasetFieldRepository fieldRepository;
    private final FileDatasetParseJobRepository parseJobRepository;
    private final DirectoryService directoryService;
    private final SearchEngine searchEngine;
    private final ObjectProvider<FileObjectStorage> storageProvider;
    private final ObjectMapper objectMapper;
    private final List<FileDatasetParser> parsers;
    private final FileDatasetTemporaryFileManager temporaryFileManager;
    private final FileDatasetContentParser contentParser;
    private final FileDatasetParseJobSubmissionService parseJobSubmissionService;
    private final FileDatasetSchemaValidator schemaValidator;
    private final TransactionTemplate transactionTemplate;
    private final CanvasFileDatasetReferenceService canvasReferenceService;

    public FileDatasetService(
            FileDatasetRepository repository,
            FileDatasetFileRepository fileRepository,
            FileDatasetTableRepository tableRepository,
            FileDatasetTableSourceRepository sourceRepository,
            FileDatasetFieldRepository fieldRepository,
            FileDatasetParseJobRepository parseJobRepository,
            DirectoryService directoryService,
            SearchEngine searchEngine,
            ObjectProvider<FileObjectStorage> storageProvider,
            ObjectMapper objectMapper,
            List<FileDatasetParser> parsers,
            FileDatasetTemporaryFileManager temporaryFileManager,
            FileDatasetContentParser contentParser,
            FileDatasetParseJobSubmissionService parseJobSubmissionService,
            FileDatasetSchemaValidator schemaValidator,
            CanvasFileDatasetReferenceService canvasReferenceService,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.fileRepository = fileRepository;
        this.tableRepository = tableRepository;
        this.sourceRepository = sourceRepository;
        this.fieldRepository = fieldRepository;
        this.parseJobRepository = parseJobRepository;
        this.directoryService = directoryService;
        this.searchEngine = searchEngine;
        this.storageProvider = storageProvider;
        this.objectMapper = objectMapper;
        this.parsers = List.copyOf(parsers);
        this.temporaryFileManager = temporaryFileManager;
        this.contentParser = contentParser;
        this.parseJobSubmissionService = parseJobSubmissionService;
        this.schemaValidator = schemaValidator;
        this.canvasReferenceService = canvasReferenceService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<FileDatasetResponse> search(SearchRequest request) {
        Page<FileDataset> result = searchEngine.search(request, FileDataset.class, repository);
        return page(result.map(this::datasetResponse));
    }

    @Transactional(readOnly = true)
    public FileDatasetResponse get(UUID id) {
        return datasetResponse(requireDataset(id));
    }

    @Transactional
    public FileDatasetResponse create(CreateFileDatasetRequest request) {
        directoryService.validateAssignment(DirectoryScope.FILE_DATASET, request.directoryId());
        validateParsingOptions(request.type(), request.parsingOptions());
        String options = writeParsingOptions(FileDatasetParsingOptionsResponse.from(request.parsingOptions()));
        FileDataset dataset = repository.saveAndFlush(FileDataset.create(
                request.directoryId(), request.name(), request.type(), options, request.description()
        ));
        return datasetResponse(dataset);
    }

    @Transactional
    public FileDatasetResponse update(UUID id, UpdateFileDatasetRequest request) {
        FileDataset dataset = requireDatasetLocked(id);
        directoryService.validateAssignment(DirectoryScope.FILE_DATASET, request.directoryId());
        validateParsingOptions(dataset.getType(), request.parsingOptions());
        String parsingOptions = writeParsingOptions(FileDatasetParsingOptionsResponse.from(request.parsingOptions()));
        boolean parsingChanged = !dataset.getParsingOptions().equals(parsingOptions);
        if (parsingChanged && (fileRepository.countByFileDatasetId(id) > 0
                || tableRepository.countByFileDatasetId(id) > 0
                || parseJobRepository.existsByFileDatasetIdAndStatusIn(id, NON_TERMINAL_JOB_STATUSES))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "文件数据集已经包含文件、表或解析任务，解析参数已经锁定"
            );
        }
        dataset.update(
                request.directoryId(), request.name(),
                parsingOptions,
                request.description()
        );
        repository.saveAndFlush(dataset);
        return datasetResponse(dataset);
    }

    @Transactional(readOnly = true)
    public PageResponse<FileDatasetFileResponse> searchFiles(UUID datasetId, SearchRequest request) {
        requireDataset(datasetId);
        Page<FileDatasetFile> result = searchEngine.search(
                request, FileDatasetFile.class, fileRepository,
                (root, query, builder) -> builder.equal(root.get("fileDatasetId"), datasetId)
        );
        return page(result.map(FileDatasetFileResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<FileDatasetTableResponse> searchTables(UUID datasetId, SearchRequest request) {
        requireDataset(datasetId);
        Page<FileDatasetTable> result = searchEngine.search(
                request, FileDatasetTable.class, tableRepository,
                (root, query, builder) -> builder.equal(root.get("fileDatasetId"), datasetId)
        );
        return page(result.map(this::tableResponse));
    }

    @Transactional(readOnly = true)
    public FileDatasetTableResponse getTable(UUID datasetId, UUID tableId) {
        requireDataset(datasetId);
        return tableResponse(requireTable(datasetId, tableId));
    }

    @Transactional(readOnly = true)
    public FileDatasetCanvasMetadataResponse queryCanvasMetadata(List<UUID> tableIds) {
        List<UUID> requestedIds = tableIds == null
                ? List.of()
                : tableIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (requestedIds.isEmpty()) {
            return new FileDatasetCanvasMetadataResponse(List.of());
        }
        Map<UUID, FileDatasetTable> tables = tableRepository.findAllById(requestedIds).stream()
                .collect(java.util.stream.Collectors.toMap(FileDatasetTable::getId, table -> table));
        Set<UUID> datasetIds = tables.values().stream()
                .map(FileDatasetTable::getFileDatasetId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, List<FileDatasetTableSource>> sources = sourceRepository
                .findByFileDatasetTableIdInOrderByFileDatasetTableIdAscSourceOrderAsc(requestedIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        FileDatasetTableSource::getFileDatasetTableId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));
        Set<UUID> fileIds = sources.values().stream().flatMap(List::stream)
                .map(FileDatasetTableSource::getSourceFileId)
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, FileDataset> datasets = repository.findAllById(datasetIds).stream()
                .collect(java.util.stream.Collectors.toMap(FileDataset::getId, dataset -> dataset));
        Map<UUID, FileDatasetFile> files = fileRepository.findAllById(fileIds).stream()
                .collect(java.util.stream.Collectors.toMap(FileDatasetFile::getId, file -> file));
        Map<UUID, List<cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField>> fields =
                fieldRepository
                        .findByFileDatasetTableIdInOrderByFileDatasetTableIdAscSortOrderAsc(requestedIds)
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField
                                        ::getFileDatasetTableId,
                                LinkedHashMap::new,
                                java.util.stream.Collectors.toList()
                        ));
        List<FileDatasetCanvasTableMetadataResponse> responses = requestedIds.stream()
                .map(tables::get)
                .filter(java.util.Objects::nonNull)
                .map(table -> {
                    FileDataset dataset = datasets.get(table.getFileDatasetId());
                    List<FileDatasetTableSource> tableSources = sources.getOrDefault(table.getId(), List.of());
                    boolean filesReady = !tableSources.isEmpty() && tableSources.stream()
                            .map(FileDatasetTableSource::getSourceFileId)
                            .map(files::get)
                            .allMatch(file -> file != null && file.getStatus() == FileDatasetFileStatus.READY);
                    if (dataset == null || tableSources.isEmpty()) {
                        log.warn(
                                "Skipping inconsistent file dataset Canvas metadata tableId={} datasetPresent={} sources={}",
                                table.getId(), dataset != null, tableSources.size()
                        );
                        return null;
                    }
                    return new FileDatasetCanvasTableMetadataResponse(
                            table.getId(),
                            dataset.getId(),
                            dataset.getName(),
                            dataset.getType(),
                            table.getCode(),
                            table.getName(),
                            table.getParseStatus(),
                            filesReady ? FileDatasetFileStatus.READY : FileDatasetFileStatus.PREPARING,
                            FileDatasetFieldResponse.from(fields.getOrDefault(table.getId(), List.of()))
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return new FileDatasetCanvasMetadataResponse(responses);
    }

    public FileDatasetUploadResponse uploadFiles(UUID datasetId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "至少需要上传一个文件");
        }
        UploadContext context = requireTransactionResult(transactionTemplate.execute(status -> uploadContext(datasetId)));
        validateUploadCount(context, files);
        FileObjectStorage storage = requireStorage();
        List<PreparedUpload> prepared = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                UploadedFile upload = validateUpload(context.type(), file);
                String objectKey = newObjectKey(upload.fileName());
                FileObjectStorage.StoredFileObject storedObject = store(storage, objectKey, file, upload);
                PreparedUpload item = new PreparedUpload(upload, objectKey, storedObject.eTag(), List.of());
                prepared.add(item);
                List<FileDatasetParser.DiscoveredTable> tables = discoverTables(storage, item);
                prepared.set(prepared.size() - 1, item.withTables(tables));
            }
            return requireTransactionResult(transactionTemplate.execute(status -> persistUploads(datasetId, prepared)));
        } catch (RuntimeException exception) {
            prepared.forEach(item -> deleteQuietly(storage, item.objectKey(), "批量上传失败后的补偿删除"));
            throw exception;
        }
    }

    public FileDatasetUploadResponse replaceFile(UUID datasetId, UUID fileId, MultipartFile multipartFile) {
        ReplacementContext context = requireTransactionResult(transactionTemplate.execute(
                status -> replacementContext(datasetId, fileId)
        ));
        UploadedFile upload = validateUpload(context.type(), multipartFile);
        FileObjectStorage storage = requireStorage();
        String objectKey = newObjectKey(upload.fileName());
        FileObjectStorage.StoredFileObject storedObject = store(storage, objectKey, multipartFile, upload);
        PreparedUpload prepared = new PreparedUpload(upload, objectKey, storedObject.eTag(), List.of());
        try {
            prepared = prepared.withTables(discoverTables(storage, prepared));
            PreparedUpload finalPrepared = prepared;
            return requireTransactionResult(transactionTemplate.execute(
                    status -> replaceStoredFile(datasetId, fileId, finalPrepared, storage)
            ));
        } catch (RuntimeException exception) {
            deleteQuietly(storage, objectKey, "替换文件失败后的补偿删除");
            throw exception;
        }
    }

    public FileDatasetContent openContent(UUID datasetId, UUID fileId) {
        ContentPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            requireDataset(datasetId);
            FileDatasetFile file = requireFile(datasetId, fileId);
            return new ContentPreparation(
                    file.getOriginalFileName(), file.getObjectKey(), file.getContentType(), file.getSizeBytes()
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
    public void deleteFile(UUID datasetId, UUID fileId) {
        FileDataset dataset = requireDatasetLocked(datasetId);
        FileDatasetFile initialFile = requireFile(datasetId, fileId);
        List<FileDatasetTableSource> fileSources =
                sourceRepository.findBySourceFileIdOrderByCreatedAtAsc(fileId);
        if (dataset.getType() != FileDatasetType.EXCEL
                && dataset.getType() != FileDatasetType.GDB
                && dataset.getType() != FileDatasetType.GPKG
                && !fileSources.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "单表文件请使用逻辑表的数据来源删除接口"
            );
        }
        List<FileDatasetParseJob> nonTerminalJobs =
                parseJobRepository.findBySourceFileIdAndStatusIn(fileId, NON_TERMINAL_JOB_STATUSES);
        List<FileDatasetTable> initialAffectedTables = tablesForFile(fileId);
        FileDatasetFile file = parseJobSubmissionService.cancelQueuedPreparationAndLockFile(
                initialFile,
                "来源文件已经删除",
                "文件正在执行准备任务，暂不能删除"
        );
        List<FileDatasetTable> affectedTables = parseJobSubmissionService.cancelQueuedAndLockTables(
                initialAffectedTables,
                "来源文件已经删除",
                "文件包含正在解析的表，暂不能替换或删除"
        );
        Set<UUID> tablesToDelete = new HashSet<>();
        if (dataset.getType() == FileDatasetType.EXCEL || dataset.getType() == FileDatasetType.GDB
                || dataset.getType() == FileDatasetType.GPKG) {
            affectedTables.forEach(table -> tablesToDelete.add(table.getId()));
        } else {
            nonTerminalJobs.stream()
                    .filter(job -> job.getLoadMode() == FileDatasetTableSourceLoadMode.INITIAL)
                    .map(FileDatasetParseJob::getFileDatasetTableId)
                    .filter(Objects::nonNull)
                    .forEach(tablesToDelete::add);
        }
        List<FileDatasetTable> deletedTables = affectedTables.stream()
                .filter(table -> tablesToDelete.contains(table.getId()))
                .toList();
        ensureTablesUnreferenced(deletedTables.stream().map(FileDatasetTable::getId).toList());
        deleteFields(deletedTables);
        sourceRepository.deleteBySourceFileId(fileId);
        sourceRepository.flush();
        deletedTables.forEach(tableRepository::delete);
        tableRepository.flush();
        fileRepository.delete(file);
        fileRepository.flush();
        deleteAfterCommit(requireStorage(), file.getObjectKey(), "删除文件后的对象清理");
        deletePrefixAfterCommit(requireStorage(), file.getMaterializedPrefix(), "删除文件后的物化前缀清理");
    }

    @Transactional
    public void delete(UUID datasetId) {
        FileDataset dataset = requireDatasetLocked(datasetId);
        List<FileDatasetFile> files = fileRepository.findByFileDatasetIdOrderByCreatedAtAsc(datasetId);
        List<FileDatasetFile> lockedFiles = files.stream().map(file ->
                parseJobSubmissionService.cancelQueuedPreparationAndLockFile(
                        file,
                        "文件数据集已经删除",
                        "文件数据集包含正在执行的文件准备任务，暂不能删除"
                )
        ).toList();
        List<FileDatasetTable> tables = parseJobSubmissionService.cancelQueuedAndLockTables(
                tableRepository.findByFileDatasetIdOrderByCreatedAtAsc(datasetId),
                "文件数据集已经删除",
                "文件包含正在解析的表，暂不能替换或删除"
        );
        ensureTablesUnreferenced(tables.stream().map(FileDatasetTable::getId).toList());
        deleteFields(tables);
        sourceRepository.deleteByFileDatasetTableIdIn(tables.stream().map(FileDatasetTable::getId).toList());
        sourceRepository.flush();
        tableRepository.deleteByFileDatasetId(datasetId);
        tableRepository.flush();
        fileRepository.deleteByFileDatasetId(datasetId);
        fileRepository.flush();
        repository.delete(dataset);
        repository.flush();
        if (!lockedFiles.isEmpty()) {
            FileObjectStorage storage = requireStorage();
            lockedFiles.forEach(file -> {
                deleteAfterCommit(storage, file.getObjectKey(), "删除文件数据集后的对象清理");
                deletePrefixAfterCommit(storage, file.getMaterializedPrefix(), "删除文件数据集后的物化前缀清理");
            });
        }
    }

    @Transactional
    public FileDatasetTableResponse updateTable(UUID datasetId, UUID tableId, UpdateFileDatasetTableRequest request) {
        requireDataset(datasetId);
        FileDatasetTable table = requireTable(datasetId, tableId);
        table.rename(request.name());
        tableRepository.saveAndFlush(table);
        return tableResponse(table);
    }

    public FileDatasetTableResponse updateTableSpatialReference(
            UUID datasetId,
            UUID tableId,
            UpdateFileDatasetTableSpatialReferenceRequest request
    ) {
        CrsReference spatialReference = new CrsReference(
                request.authority().trim().toUpperCase(Locale.ROOT), request.code()
        );
        SpatialReferencePreparation preparation = requireTransactionResult(transactionTemplate.execute(status ->
                prepareSpatialReferenceUpdate(datasetId, tableId)
        ));
        List<FileDatasetParser.ParseResult> parsedSources = new ArrayList<>(preparation.sources().size());
        try {
            for (SpatialSourceSnapshot source : preparation.sources()) {
                FileDatasetParser.ParseResult parsed = contentParser.parse(
                        source.input(spatialReference.code()), 1_000
                );
                if (parsed.fields().isEmpty()) {
                    throw new FileDatasetParsingException("未识别到可用字段");
                }
                requireEffectiveSpatialReference(parsed, spatialReference);
                if (!parsedSources.isEmpty()) {
                    schemaValidator.requireCompatible(parsedSources.getFirst(), parsed);
                }
                parsedSources.add(parsed);
            }
        } catch (FileStorageObjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件内容不存在", exception);
        } catch (FileStorageException exception) {
            throw storageUnavailable(exception);
        } catch (FileDatasetParsingInfrastructureException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
        } catch (FileDatasetParsingException | IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "无法按指定空间参考重新解析表：" + safeParseError(exception),
                    exception
            );
        }
        return requireTransactionResult(transactionTemplate.execute(status -> applySpatialReferenceUpdate(
                preparation, parsedSources, spatialReference
        )));
    }

    @Transactional(readOnly = true)
    public List<FileDatasetTableSourceResponse> tableSources(UUID datasetId, UUID tableId) {
        requireDataset(datasetId);
        requireTable(datasetId, tableId);
        return sourceRepository.findByFileDatasetTableIdOrderBySourceOrderAsc(tableId).stream()
                .map(FileDatasetTableSourceResponse::from)
                .toList();
    }

    public FileDatasetTableLoadSubmissionResponse append(
            UUID datasetId,
            UUID tableId,
            MultipartFile file
    ) {
        return submitTableLoad(datasetId, tableId, null, FileDatasetTableSourceLoadMode.APPEND, file);
    }

    public FileDatasetTableLoadSubmissionResponse replaceData(
            UUID datasetId,
            UUID tableId,
            MultipartFile file
    ) {
        return submitTableLoad(datasetId, tableId, null, FileDatasetTableSourceLoadMode.REPLACE_ALL, file);
    }

    public FileDatasetTableLoadSubmissionResponse replaceSource(
            UUID datasetId,
            UUID tableId,
            UUID sourceId,
            MultipartFile file
    ) {
        return submitTableLoad(datasetId, tableId, sourceId, FileDatasetTableSourceLoadMode.REPLACE_SOURCE, file);
    }

    @Transactional
    public void deleteSource(UUID datasetId, UUID tableId, UUID sourceId) {
        FileDataset dataset = requireDatasetLocked(datasetId);
        if (dataset.getType() == FileDatasetType.GPKG) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "GeoPackage 仅支持整文件删除或替换，不能单独删除图层来源"
            );
        }
        FileDatasetTable table = tableRepository.findLockedByIdAndFileDatasetId(tableId, datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集表不存在"));
        FileDatasetTableSource source = sourceRepository.findLockedById(sourceId)
                .filter(candidate -> tableId.equals(candidate.getFileDatasetTableId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "表数据来源不存在"));
        if (table.getCurrentLoadJobId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "逻辑表正在装载，暂不能删除来源");
        }
        FileDatasetFile file = requireFile(datasetId, source.getSourceFileId());
        List<FileDatasetTableSource> current = currentSources(tableId);
        if (current.size() == 1) {
            ensureTablesUnreferenced(List.of(tableId));
            fieldRepository.deleteByFileDatasetTableId(tableId);
            sourceRepository.deleteByFileDatasetTableId(tableId);
            tableRepository.delete(table);
        } else {
            sourceRepository.delete(source);
            List<FileDatasetTableSource> remaining = current.stream()
                    .filter(item -> !item.getId().equals(sourceId))
                    .toList();
            for (int index = 0; index < remaining.size(); index++) {
                remaining.get(index).setSourceOrder(index);
            }
            sourceRepository.saveAll(remaining);
        }
        sourceRepository.flush();
        tableRepository.flush();
        fieldRepository.flush();
        if (sourceRepository.existsBySourceFileId(file.getId())
                || parseJobRepository.existsBySourceFileIdAndStatusIn(
                        file.getId(), NON_TERMINAL_JOB_STATUSES
                )) {
            return;
        }
        fileRepository.delete(file);
        fileRepository.flush();
        deleteAfterCommit(requireStorage(), file.getObjectKey(), "删除表数据来源后的对象清理");
        deletePrefixAfterCommit(requireStorage(), file.getMaterializedPrefix(), "删除表数据来源后的物化前缀清理");
    }

    private FileDatasetTableLoadSubmissionResponse submitTableLoad(
            UUID datasetId,
            UUID tableId,
            UUID replacesSourceId,
            FileDatasetTableSourceLoadMode mode,
            MultipartFile multipartFile
    ) {
        FileDatasetType type = requireTransactionResult(transactionTemplate.execute(status -> {
            FileDataset dataset = requireDataset(datasetId);
            requireTable(datasetId, tableId);
            ensureTableLoadSupported(dataset.getType());
            return dataset.getType();
        }));
        UploadedFile upload = validateUpload(type, multipartFile);
        FileObjectStorage storage = requireStorage();
        String objectKey = newObjectKey(upload.fileName());
        FileObjectStorage.StoredFileObject stored = store(storage, objectKey, multipartFile, upload);
        try {
            return requireTransactionResult(transactionTemplate.execute(status -> persistTableLoad(
                    datasetId, tableId, replacesSourceId, mode,
                    new PreparedUpload(upload, objectKey, stored.eTag(), List.of())
            )));
        } catch (RuntimeException exception) {
            deleteQuietly(storage, objectKey, "提交表数据装载失败后的补偿删除");
            throw exception;
        }
    }

    private FileDatasetTableLoadSubmissionResponse persistTableLoad(
            UUID datasetId,
            UUID tableId,
            UUID replacesSourceId,
            FileDatasetTableSourceLoadMode mode,
            PreparedUpload prepared
    ) {
        FileDataset dataset = requireDatasetLocked(datasetId);
        ensureTableLoadSupported(dataset.getType());
        FileDatasetTable table = tableRepository.findLockedByIdAndFileDatasetId(tableId, datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集表不存在"));
        if (!table.hasData()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有存在当前数据的逻辑表才能追加或覆盖");
        }
        if (table.getCurrentLoadJobId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "逻辑表已经存在正在执行的数据装载");
        }
        if (mode == FileDatasetTableSourceLoadMode.REPLACE_SOURCE) {
            FileDatasetTableSource target = sourceRepository.findByIdAndFileDatasetTableId(replacesSourceId, tableId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "被替换来源不是当前来源"));
            replacesSourceId = target.getId();
        }
        FileDatasetFile file = fileRepository.saveAndFlush(FileDatasetFile.create(
                datasetId, prepared.upload().fileName(), prepared.upload().format(),
                prepared.upload().compression(), prepared.objectKey(), prepared.upload().contentType(),
                prepared.upload().sizeBytes(), prepared.eTag()
        ));
        String sourceKey = dataset.getType() == FileDatasetType.SHP
                ? "data.shp" : SINGLE_TABLE_SOURCE_KEY;
        FileDatasetParseJobSubmissionService.Submission submission = null;
        FileDatasetParseJobSubmissionService.FilePreparationSubmission preparation = null;
        if (!file.requiresPreparation()) {
            submission = parseJobSubmissionService.enqueueTableValidation(
                    dataset, file, table, mode, replacesSourceId, tableBaseName(prepared.upload()), sourceKey
            );
        } else {
            preparation = parseJobSubmissionService.enqueuePreparation(
                    dataset, file, table, mode, replacesSourceId, tableBaseName(prepared.upload()), sourceKey
            );
        }
        UUID jobId = submission != null ? submission.job().getId() : preparation.job().getId();
        return new FileDatasetTableLoadSubmissionResponse(
                jobId, FileDatasetFileResponse.from(file), tableResponse(table)
        );
    }

    private static void ensureTableLoadSupported(FileDatasetType type) {
        if (type == FileDatasetType.EXCEL || type == FileDatasetType.GDB || type == FileDatasetType.GPKG) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                    "Excel/GDB/GPKG 暂不支持表级追加或覆盖，请使用整文件替换"
            );
        }
    }

    @Transactional(readOnly = true)
    public FileDatasetSchemaResponse schema(UUID datasetId, UUID tableId) {
        requireDataset(datasetId);
        FileDatasetTable table = requireTable(datasetId, tableId);
        if (table.getParseStatus() != FileDatasetParseStatus.READY
                && table.getParseStatus() != FileDatasetParseStatus.SCHEMA_READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先成功解析该表");
        }
        return new FileDatasetSchemaResponse(tableId, parsedFields(tableId));
    }

    public FileDatasetPreviewResponse preview(UUID datasetId, UUID tableId, int limit) {
        PreviewPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            FileDataset dataset = requireDataset(datasetId);
            FileDatasetTable table = requireTable(datasetId, tableId);
            if (table.getParseStatus() == FileDatasetParseStatus.SCHEMA_READY) {
                Object storedReason = readParsedMetadata(table.getParsedMetadata())
                        .sourceMetadata().get("previewUnavailableReason");
                String detail = storedReason instanceof String reason && hasText(reason)
                        ? reason
                        : dataset.getType() == FileDatasetType.GDB
                                ? "该 GDB 图层仅支持 Schema，暂不支持数据预览"
                                : "该表仅支持 Schema，暂不支持数据预览";
                throw new ResponseStatusException(HttpStatus.CONFLICT, detail);
            }
            if (table.getParseStatus() != FileDatasetParseStatus.READY) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "请先成功解析该表");
            }
            List<FileDatasetTableSource> sources = currentSources(tableId);
            if (sources.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "逻辑表没有当前数据来源");
            }
            List<FileDatasetFieldResponse> previewFields = parsedFields(tableId);
            if (dataset.getType() == FileDatasetType.GDB
                    || dataset.getType() == FileDatasetType.SHP
                    || dataset.getType() == FileDatasetType.GEOJSON
                    || dataset.getType() == FileDatasetType.GEOJSONL
                    || dataset.getType() == FileDatasetType.GEOPARQUET
                    || dataset.getType() == FileDatasetType.GPKG) {
                previewFields = previewFields.stream()
                        .filter(field -> field.fieldType() != PlatformDataType.GEOMETRY)
                        .toList();
            }
            return new PreviewPreparation(
                    sources.stream().map(source -> parseInput(
                            dataset,
                            requireFile(datasetId, source.getSourceFileId()),
                            source,
                            table.getSpatialReferenceOverride() == null
                                    ? null : table.getSpatialReferenceOverride().code()
                    )).toList(),
                    previewFields
            );
        }));
        try {
            List<List<Object>> rows = new ArrayList<>();
            int effectiveLimit = limit;
            boolean truncated = false;
            for (int index = 0; index < preparation.inputs().size() && rows.size() < effectiveLimit; index++) {
                int remaining = effectiveLimit - rows.size();
                FileDatasetParser.ParseResult result = parseContent(preparation.inputs().get(index), remaining);
                if (!result.previewSupported()) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "至少一个当前来源不支持安全预览，无法返回部分数据"
                    );
                }
                Object sourcePreviewLimit = result.sourceMetadata().get("previewRecordLimit");
                if (sourcePreviewLimit instanceof Number number && number.intValue() > 0) {
                    effectiveLimit = Math.min(effectiveLimit, number.intValue() + rows.size());
                }
                rows.addAll(result.rows().stream()
                        .map(row -> preparation.fields().stream().map(field -> row.get(field.name())).toList())
                        .toList());
                truncated = result.truncated()
                        || (rows.size() >= effectiveLimit && index < preparation.inputs().size() - 1);
                if (result.truncated()) {
                    break;
                }
            }
            return new FileDatasetPreviewResponse(preparation.fields(), rows, limit, truncated);
        } catch (FileStorageObjectNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件内容不存在", exception);
        } catch (FileStorageException exception) {
            throw storageUnavailable(exception);
        } catch (FileDatasetParsingInfrastructureException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
        } catch (FileDatasetParsingException | IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "无法按已保存的配置预览表：" + safeParseError(exception), exception
            );
        }
    }

    private UploadContext uploadContext(UUID datasetId) {
        FileDataset dataset = requireDataset(datasetId);
        return new UploadContext(dataset.getType(), fileRepository.countByFileDatasetId(datasetId));
    }

    private ReplacementContext replacementContext(UUID datasetId, UUID fileId) {
        FileDataset dataset = requireDataset(datasetId);
        requireFile(datasetId, fileId);
        if (dataset.getType() != FileDatasetType.EXCEL && dataset.getType() != FileDatasetType.GDB
                && dataset.getType() != FileDatasetType.GPKG) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "单表文件请使用逻辑表的数据来源替换接口"
            );
        }
        List<FileDatasetTable> tables = tablesForFile(fileId);
        ensureTablesMutable(tables);
        ensureTablesUnreferenced(tables.stream().map(FileDatasetTable::getId).toList());
        return new ReplacementContext(dataset.getType());
    }

    private FileDatasetUploadResponse persistUploads(UUID datasetId, List<PreparedUpload> prepared) {
        FileDataset dataset = requireDatasetLocked(datasetId);
        if ((dataset.getType() == FileDatasetType.EXCEL || dataset.getType() == FileDatasetType.GDB
                || dataset.getType() == FileDatasetType.GPKG)
                && fileRepository.countByFileDatasetId(datasetId) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    datasetTypeDisplayName(dataset.getType()) + " 文件数据集只允许上传一个文件"
            );
        }
        Set<String> usedCodes = existingTableCodes(datasetId, null);
        List<FileDatasetFile> savedFiles = new ArrayList<>();
        List<FileDatasetTable> savedTables = new ArrayList<>();
        List<UUID> jobIds = new ArrayList<>();
        for (PreparedUpload item : prepared) {
            FileDatasetFile file = fileRepository.saveAndFlush(FileDatasetFile.create(
                    datasetId, item.upload().fileName(), item.upload().format(), item.upload().compression(), item.objectKey(),
                    item.upload().contentType(), item.upload().sizeBytes(), item.eTag()
            ));
            savedFiles.add(file);
            if (file.requiresPreparation()) {
                jobIds.add(parseJobSubmissionService.enqueuePreparation(dataset, file).job().getId());
            } else {
                savedTables.addAll(createTables(dataset, file, item.tables(), usedCodes, jobIds));
            }
        }
        return uploadResponse(savedFiles, savedTables, jobIds);
    }

    private FileDatasetUploadResponse replaceStoredFile(
            UUID datasetId,
            UUID fileId,
            PreparedUpload prepared,
            FileObjectStorage storage
    ) {
        FileDataset dataset = requireDatasetLocked(datasetId);
        FileDatasetFile file = parseJobSubmissionService.cancelQueuedPreparationAndLockFile(
                requireFile(datasetId, fileId),
                "来源文件已经替换",
                "文件正在执行准备任务，暂不能替换"
        );
        List<FileDatasetTable> oldTables = parseJobSubmissionService.cancelQueuedAndLockTables(
                tablesForFile(fileId),
                "来源文件已经替换",
                "文件包含正在解析的表，暂不能替换或删除"
        );
        ensureTablesUnreferenced(oldTables.stream().map(FileDatasetTable::getId).toList());
        String oldObjectKey = file.getObjectKey();
        String oldMaterializedPrefix = file.getMaterializedPrefix();
        deleteFields(oldTables);
        sourceRepository.deleteBySourceFileId(fileId);
        sourceRepository.flush();
        oldTables.forEach(tableRepository::delete);
        tableRepository.flush();
        file.replace(
                prepared.upload().fileName(), prepared.upload().format(), prepared.upload().compression(), prepared.objectKey(),
                prepared.upload().contentType(), prepared.upload().sizeBytes(), prepared.eTag()
        );
        fileRepository.saveAndFlush(file);
        Set<String> usedCodes = existingTableCodes(datasetId, fileId);
        List<FileDatasetTable> newTables;
        List<UUID> jobIds = new ArrayList<>();
        if (file.requiresPreparation()) {
            newTables = List.of();
            jobIds.add(parseJobSubmissionService.enqueuePreparation(dataset, file).job().getId());
        } else {
            newTables = createTables(dataset, file, prepared.tables(), usedCodes, jobIds);
        }
        deleteAfterCommit(storage, oldObjectKey, "替换文件后的旧对象清理");
        deletePrefixAfterCommit(storage, oldMaterializedPrefix, "替换文件后的旧物化前缀清理");
        return uploadResponse(List.of(file), newTables, jobIds);
    }

    private List<FileDatasetTable> createTables(
            FileDataset dataset,
            FileDatasetFile file,
            List<FileDatasetParser.DiscoveredTable> discovered,
            Set<String> usedCodes,
            List<UUID> jobIds
    ) {
        List<FileDatasetTable> tables = new ArrayList<>(discovered.size());
        for (FileDatasetParser.DiscoveredTable discoveredTable : discovered) {
            String code = uniqueCode(discoveredTable.sourceName(), usedCodes);
            FileDatasetTable table = tableRepository.saveAndFlush(FileDatasetTable.create(
                    dataset.getId(), code, discoveredTable.sourceName()
            ));
            String sourceKey = (dataset.getType() == FileDatasetType.EXCEL || dataset.getType() == FileDatasetType.GPKG)
                    ? discoveredTable.sourceName() : SINGLE_TABLE_SOURCE_KEY;
            FileDatasetParseJobSubmissionService.Submission submission =
                    parseJobSubmissionService.enqueueTableValidation(
                            dataset, file, table, FileDatasetTableSourceLoadMode.INITIAL, null,
                            discoveredTable.sourceName(), sourceKey
                    );
            jobIds.add(submission.job().getId());
            tables.add(table);
        }
        return List.copyOf(tables);
    }

    private FileDatasetUploadResponse uploadResponse(
            List<FileDatasetFile> files,
            List<FileDatasetTable> tables,
            List<UUID> jobIds
    ) {
        return new FileDatasetUploadResponse(
                files.stream().map(FileDatasetFileResponse::from).toList(),
                tables.stream().map(this::tableResponse).toList(),
                jobIds
        );
    }

    private FileDatasetParser.ParseResult parseContent(
            FileDatasetContentParser.Input input,
            int recordLimit
    ) throws IOException {
        return contentParser.parse(input, recordLimit);
    }

    private List<FileDatasetParser.DiscoveredTable> discoverTables(FileObjectStorage storage, PreparedUpload prepared) {
        if (prepared.upload().format() == FileDatasetFormat.GDB
                || prepared.upload().format() == FileDatasetFormat.SHP) {
            return List.of();
        }
        if (prepared.upload().format() != FileDatasetFormat.XLS && prepared.upload().format() != FileDatasetFormat.XLSX) {
            return List.of(new FileDatasetParser.DiscoveredTable(tableBaseName(prepared.upload()), 0));
        }
        FileDatasetParser parser = requireParser(prepared.upload().format());
        FileObjectStorage.FileObjectContent content;
        try {
            content = storage.open(prepared.objectKey());
        } catch (FileStorageException exception) {
            throw storageUnavailable(exception);
        }
        boolean completed = false;
        try (content) {
            Path localFile = temporaryFileManager.materialize(
                    content.inputStream(),
                    content.contentLength() >= 0 ? content.contentLength() : prepared.upload().sizeBytes()
            );
            try {
                List<FileDatasetParser.DiscoveredTable> tables = parser.discoverTables(
                        new FileDatasetParseSource.LocalFile(localFile)
                );
                if (tables.isEmpty()) {
                    throw new FileDatasetParsingException("Excel 文件不包含工作表");
                }
                completed = true;
                return tables;
            } finally {
                temporaryFileManager.delete(localFile);
            }
        } catch (FileDatasetParsingException | IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "无法识别 Excel 工作表：" + safeParseError(exception), exception
            );
        } finally {
            if (!completed) {
                abortContent(content);
            }
        }
    }

    private void validateParsingOptions(FileDatasetType type, FileDatasetParsingOptionsRequest options) {
        boolean compatible = switch (type) {
            case CSV, TSV -> options instanceof FileDatasetParsingOptionsRequest.Csv;
            case TXT -> options instanceof FileDatasetParsingOptionsRequest.Text;
            case JSON -> options instanceof FileDatasetParsingOptionsRequest.Json;
            case JSONL -> options instanceof FileDatasetParsingOptionsRequest.JsonLines;
            case GEOJSON -> options instanceof FileDatasetParsingOptionsRequest.GeoJson;
            case GEOJSONL -> options instanceof FileDatasetParsingOptionsRequest.GeoJsonLines;
            case GEOPARQUET -> options instanceof FileDatasetParsingOptionsRequest.GeoParquet;
            case GPKG -> options instanceof FileDatasetParsingOptionsRequest.GeoPackage;
            case EXCEL -> options instanceof FileDatasetParsingOptionsRequest.Spreadsheet;
            case PARQUET -> options instanceof FileDatasetParsingOptionsRequest.Parquet;
            case AVRO -> options instanceof FileDatasetParsingOptionsRequest.Avro;
            case GDB -> options instanceof FileDatasetParsingOptionsRequest.Gdb;
            case SHP -> options instanceof FileDatasetParsingOptionsRequest.Shp;
        };
        if (!compatible) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "解析参数类型与文件数据集类型不匹配");
        }
        switch (options) {
            case FileDatasetParsingOptionsRequest.Csv value -> {
                validateCharset(value.charset());
                if (type == FileDatasetType.TSV && !"\t".equals(value.fieldDelimiter())) {
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
            case FileDatasetParsingOptionsRequest.GeoJson ignored -> { }
            case FileDatasetParsingOptionsRequest.GeoJsonLines ignored -> { }
            case FileDatasetParsingOptionsRequest.GeoParquet ignored -> { }
            case FileDatasetParsingOptionsRequest.GeoPackage ignored -> { }
            case FileDatasetParsingOptionsRequest.Spreadsheet value -> {
                if (value.dataStartRowIndex() <= value.headerRowIndex()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据起始行必须位于表头行之后");
                }
            }
            case FileDatasetParsingOptionsRequest.Parquet ignored -> { }
            case FileDatasetParsingOptionsRequest.Avro ignored -> { }
            case FileDatasetParsingOptionsRequest.Gdb ignored -> { }
            case FileDatasetParsingOptionsRequest.Shp value -> {
                if (hasText(value.dbfCharsetOverride())) {
                    validateCharset(value.dbfCharsetOverride());
                }
                validateCharset(value.dbfFallbackCharset());
            }
        }
    }

    private UploadedFile validateUpload(FileDatasetType type, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        FileDatasetCompression compression = type == FileDatasetType.GDB || type == FileDatasetType.SHP
                ? FileDatasetCompression.ZIP : compressionOf(fileName);
        FileDatasetFormat format = physicalFormat(type, fileName, compression);
        validateContentSignature(format, compression, file);
        String contentType = switch (compression) {
            case GZIP -> "application/gzip";
            case ZIP -> "application/zip";
            case NONE -> normalizeContentType(file.getContentType());
        };
        return new UploadedFile(fileName, contentType, file.getSize(), compression, format);
    }

    private FileDatasetFormat physicalFormat(
            FileDatasetType type,
            String fileName,
            FileDatasetCompression compression
    ) {
        String extension = extensionOf(baseFileName(fileName, compression));
        FileDatasetFormat format = switch (type) {
            case CSV -> requireExtension(extension, type, Set.of("csv"), FileDatasetFormat.CSV);
            case TSV -> requireExtension(extension, type, Set.of("tsv"), FileDatasetFormat.TSV);
            case TXT -> requireExtension(extension, type, Set.of("txt"), FileDatasetFormat.TXT);
            case JSON -> requireExtension(extension, type, Set.of("json"), FileDatasetFormat.JSON);
            case JSONL -> requireExtension(extension, type, Set.of("jsonl", "ndjson"), FileDatasetFormat.JSONL);
            case GEOJSON -> requireExtension(extension, type, Set.of("geojson", "json"), FileDatasetFormat.GEOJSON);
            case GEOJSONL -> requireExtension(
                    extension, type, Set.of("geojsonl", "ndgeojson", "jsonl", "ndjson"), FileDatasetFormat.GEOJSONL
            );
            case GEOPARQUET -> requireExtension(extension, type, Set.of("parquet"), FileDatasetFormat.GEOPARQUET);
            case GPKG -> requireExtension(extension, type, Set.of("gpkg"), FileDatasetFormat.GPKG);
            case PARQUET -> requireExtension(extension, type, Set.of("parquet"), FileDatasetFormat.PARQUET);
            case AVRO -> requireExtension(extension, type, Set.of("avro"), FileDatasetFormat.AVRO);
            case GDB -> requireExtension(extension, type, Set.of("zip"), FileDatasetFormat.GDB);
            case SHP -> requireExtension(extension, type, Set.of("zip"), FileDatasetFormat.SHP);
            case EXCEL -> switch (extension) {
                case "xls" -> FileDatasetFormat.XLS;
                case "xlsx" -> FileDatasetFormat.XLSX;
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXCEL 类型只接受 .xls 或 .xlsx 文件");
            };
        };
        if (compression == FileDatasetCompression.GZIP && !supportsGzip(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, type + " 类型暂不支持 GZIP 外层压缩");
        }
        if ((format == FileDatasetFormat.GDB || format == FileDatasetFormat.SHP)
                && compression != FileDatasetCompression.ZIP) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, type + " 类型只接受 .zip 归档");
        }
        return format;
    }

    private static FileDatasetFormat requireExtension(
            String extension,
            FileDatasetType type,
            Set<String> allowed,
            FileDatasetFormat format
    ) {
        if (!allowed.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, type + " 类型的文件扩展名不匹配");
        }
        return format;
    }

    private void validateUploadCount(UploadContext context, List<MultipartFile> files) {
        if (files.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每次只能上传一个文件");
        }
        if ((context.type() == FileDatasetType.EXCEL || context.type() == FileDatasetType.GDB
                || context.type() == FileDatasetType.GPKG)
                && context.existingFileCount() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    datasetTypeDisplayName(context.type()) + " 文件数据集只允许存在一个文件"
            );
        }
    }

    private static String datasetTypeDisplayName(FileDatasetType type) {
        return switch (type) {
            case EXCEL -> "Excel";
            case GDB -> "FileGDB";
            case GPKG -> "GeoPackage";
            default -> type.name();
        };
    }

    private void validateContentSignature(FileDatasetFormat format, FileDatasetCompression compression, MultipartFile file) {
        if (compression == FileDatasetCompression.GZIP && !hasSignature(file, 0x1F, 0x8B)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件扩展名为 GZIP，但内容不是有效 GZIP");
        }
        if (compression == FileDatasetCompression.NONE && hasSignature(file, 0x1F, 0x8B)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "检测到 GZIP 内容，请将文件名补全为 .gz");
        }
        if (compression == FileDatasetCompression.ZIP && !hasSignature(file, 'P', 'K')) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传内容不是有效的 ZIP 归档");
        }
        if (compression != FileDatasetCompression.NONE) {
            return;
        }
        switch (format) {
            case AVRO -> {
                if (!hasSignature(file, 'O', 'b', 'j', 1)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AVRO 文件内容不是有效的 Object Container File");
                }
            }
            case PARQUET, GEOPARQUET -> {
                if (!hasSignature(file, 'P', 'A', 'R', '1')) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PARQUET 文件头无效");
                }
            }
            case XLS -> {
                if (!hasSignature(file, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "XLS 文件头无效");
                }
            }
            case XLSX -> {
                if (!hasSignature(file, 'P', 'K', 0x03, 0x04)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "XLSX 文件头无效");
                }
            }
            case GPKG -> {
                if (!hasSignature(file, 'S', 'Q', 'L', 'i', 't', 'e', ' ', 'f', 'o', 'r', 'm', 'a', 't', ' ', '3', 0)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GPKG 文件头无效");
                }
            }
            case CSV, TSV, TXT, JSON, JSONL, GEOJSON, GEOJSONL, GDB, SHP -> { }
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

    private FileDatasetResponse datasetResponse(FileDataset dataset) {
        return FileDatasetResponse.from(
                dataset, readParsingOptions(dataset.getParsingOptions()),
                fileRepository.countByFileDatasetId(dataset.getId()),
                tableRepository.countByFileDatasetId(dataset.getId()),
                tableRepository.countByFileDatasetIdAndParseStatus(dataset.getId(), FileDatasetParseStatus.READY)
                        + tableRepository.countByFileDatasetIdAndParseStatus(
                                dataset.getId(), FileDatasetParseStatus.SCHEMA_READY
                ),
                fileRepository.countByFileDatasetId(dataset.getId()) > 0
                        || tableRepository.countByFileDatasetId(dataset.getId()) > 0
                        || parseJobRepository.existsByFileDatasetIdAndStatusIn(
                                dataset.getId(), NON_TERMINAL_JOB_STATUSES
                        )
        );
    }

    private FileDatasetTableResponse tableResponse(FileDatasetTable table) {
        List<FileDatasetTableSource> sources = currentSources(table.getId());
        return FileDatasetTableResponse.from(
                table,
                readParsedMetadata(table.getParsedMetadata()),
                sources.size(),
                sources.stream().mapToLong(FileDatasetTableSource::getRowCount).sum()
        );
    }

    private List<FileDatasetFieldResponse> parsedFields(UUID tableId) {
        return FileDatasetFieldResponse.from(fieldRepository.findByFileDatasetTableIdOrderBySortOrderAsc(tableId));
    }

    private void deleteFields(List<FileDatasetTable> tables) {
        List<UUID> ids = tables.stream().map(FileDatasetTable::getId).toList();
        if (!ids.isEmpty()) {
            fieldRepository.deleteByFileDatasetTableIdIn(ids);
            fieldRepository.flush();
        }
    }

    private void ensureTablesMutable(List<FileDatasetTable> tables) {
        if (tables.stream().anyMatch(table -> table.getParseStatus() == FileDatasetParseStatus.PARSING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件包含正在解析的表，暂不能替换或删除");
        }
    }

    private void ensureTablesUnreferenced(List<UUID> tableIds) {
        canvasReferenceService.ensureTablesUnreferenced(tableIds);
    }

    private Set<String> existingTableCodes(UUID datasetId, UUID excludedFileId) {
        Set<String> codes = new HashSet<>();
        Set<UUID> excludedTableIds = excludedFileId == null
                ? Set.of()
                : sourceRepository.findBySourceFileIdOrderByCreatedAtAsc(excludedFileId).stream()
                        .map(FileDatasetTableSource::getFileDatasetTableId)
                        .collect(java.util.stream.Collectors.toSet());
        for (FileDatasetTable table : tableRepository.findByFileDatasetIdOrderByCreatedAtAsc(datasetId)) {
            if (!excludedTableIds.contains(table.getId())) {
                codes.add(table.getCode());
            }
        }
        return codes;
    }

    private static String uniqueCode(String name, Set<String> usedCodes) {
        StringBuilder normalized = new StringBuilder();
        boolean separator = false;
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                if (separator && !normalized.isEmpty()) {
                    normalized.append('_');
                }
                normalized.append(Character.toLowerCase(character));
                separator = false;
            } else {
                separator = true;
            }
        }
        String base = normalized.isEmpty() ? "table" : normalized.toString();
        if (base.length() > 100) {
            base = base.substring(0, 100);
        }
        String candidate = base;
        int suffix = 2;
        while (!usedCodes.add(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }

    private FileDataset requireDataset(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集不存在"));
    }

    private FileDataset requireDatasetLocked(UUID id) {
        return repository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集不存在"));
    }

    private FileDatasetFile requireFile(UUID datasetId, UUID fileId) {
        return fileRepository.findByIdAndFileDatasetId(fileId, datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集文件不存在"));
    }

    private FileDatasetTable requireTable(UUID datasetId, UUID tableId) {
        return tableRepository.findByIdAndFileDatasetId(tableId, datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集表不存在"));
    }

    private FileDatasetParser requireParser(FileDatasetFormat format) {
        return parsers.stream().filter(parser -> parser.supports(format)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持 " + format + " 格式解析"));
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

    private String writeParsedMetadata(FileDatasetParser.ParseResult result) {
        try {
            return objectMapper.writeValueAsString(new FileDatasetParsedMetadata(
                    result.rows().size(), result.truncated(), result.sourceMetadata()
            ));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存文件解析元数据", exception);
        }
    }

    private String writeSourceMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(metadata));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法保存文件来源元数据", exception);
        }
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

    private void abortContent(FileObjectStorage.FileObjectContent content) {
        try {
            content.abort();
        } catch (RuntimeException exception) {
            log.warn("中止文件对象读取失败", exception);
        }
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

    private void deletePrefixAfterCommit(FileObjectStorage storage, String prefix, String operation) {
        if (!hasText(prefix)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletePrefixQuietly(storage, prefix, operation);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deletePrefixQuietly(storage, prefix, operation);
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

    private void deletePrefixQuietly(FileObjectStorage storage, String prefix, String operation) {
        try {
            storage.deletePrefix(prefix);
        } catch (FileStorageException exception) {
            log.warn("{}失败，prefix={}", operation, prefix, exception);
        }
    }

    private ResponseStatusException storageUnavailable(FileStorageException exception) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件对象存储不可用", exception);
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
            case CSV, TSV, TXT, JSONL, GEOJSON, GEOJSONL -> true;
            case JSON, XLS, XLSX, PARQUET, GEOPARQUET, GPKG, AVRO, GDB, SHP -> false;
        };
    }

    private static String tableBaseName(UploadedFile upload) {
        String value = baseFileName(upload.fileName(), upload.compression());
        int extensionStart = value.lastIndexOf('.');
        String base = extensionStart > 0 ? value.substring(0, extensionStart) : value;
        return base.isBlank() ? "table" : base;
    }

    private String newObjectKey(String fileName) {
        String extension = extensionOf(fileName);
        String suffix = extension.matches("[a-z0-9]{1,16}") ? "." + extension : "";
        return "file-datasets/" + UUID.randomUUID() + suffix;
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
        return index < 1 || index == fileName.length() - 1
                ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static String safeParseError(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "文件内容或解析参数无效" : message;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static FileDatasetContentParser.Input parseInput(
            FileDataset dataset,
            FileDatasetFile file,
            FileDatasetTableSource source
    ) {
        return parseInput(dataset, file, source, null);
    }

    private static FileDatasetContentParser.Input parseInput(
            FileDataset dataset,
            FileDatasetFile file,
            FileDatasetTableSource source,
            Integer epsgCodeOverride
    ) {
        return new FileDatasetContentParser.Input(
                file.getFormat(), file.getCompression(),
                file.getStorageKind() != FileDatasetStorageKind.SINGLE_OBJECT
                        ? file.getMaterializedPrefix() : file.getObjectKey(),
                file.getSizeBytes(),
                dataset.getParsingOptions(), source.getSourceKey(), epsgCodeOverride
        );
    }

    private SpatialReferencePreparation prepareSpatialReferenceUpdate(UUID datasetId, UUID tableId) {
        FileDataset dataset = requireDatasetLocked(datasetId);
        if (dataset.getType() != FileDatasetType.GDB && dataset.getType() != FileDatasetType.SHP) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "只有 GDB 和 SHP 文件数据集支持确认空间参考"
            );
        }
        FileDatasetTable table = tableRepository.findLockedByIdAndFileDatasetId(tableId, datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集表不存在"));
        if (!table.hasData()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已经就绪的逻辑表才能确认空间参考");
        }
        if (table.getCurrentLoadJobId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "逻辑表正在装载，暂不能确认空间参考");
        }
        List<FileDatasetTableSource> sources = currentSources(tableId);
        if (sources.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "逻辑表没有当前数据来源");
        }
        Map<UUID, FileDatasetFile> files = fileRepository.findAllById(
                        sources.stream().map(FileDatasetTableSource::getSourceFileId).distinct().toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(FileDatasetFile::getId, file -> file));
        List<SpatialSourceSnapshot> snapshots = sources.stream().map(source -> {
            FileDatasetFile file = files.get(source.getSourceFileId());
            if (file == null || !datasetId.equals(file.getFileDatasetId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "逻辑表来源文件不存在");
            }
            if (file.getStatus() != FileDatasetFileStatus.READY) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "逻辑表来源文件尚未准备完成");
            }
            String effectiveObjectKey = file.getStorageKind() == FileDatasetStorageKind.SINGLE_OBJECT
                    ? file.getObjectKey() : file.getMaterializedPrefix();
            if (!hasText(effectiveObjectKey)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "逻辑表来源文件缺少可读取对象");
            }
            return new SpatialSourceSnapshot(
                    source.getId(), source.getUpdatedAt(), source.getSourceFileId(),
                    source.getSchemaFingerprint(), source.getSourceMetadata(),
                    file.getUpdatedAt(), file.getStatus(), file.getStorageEtag(),
                    file.getFormat(), file.getCompression(), effectiveObjectKey,
                    file.getSizeBytes(), dataset.getParsingOptions(), source.getSourceKey()
            );
        }).toList();
        return new SpatialReferencePreparation(
                datasetId, dataset.getUpdatedAt(), dataset.getParsingOptions(),
                tableId, table.getUpdatedAt(), snapshots
        );
    }

    private FileDatasetTableResponse applySpatialReferenceUpdate(
            SpatialReferencePreparation preparation,
            List<FileDatasetParser.ParseResult> parsedSources,
            CrsReference spatialReference
    ) {
        FileDataset dataset = requireDatasetLocked(preparation.datasetId());
        FileDatasetTable table = tableRepository.findLockedByIdAndFileDatasetId(
                        preparation.tableId(), preparation.datasetId()
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集表不存在"));
        if (!Objects.equals(dataset.getUpdatedAt(), preparation.datasetUpdatedAt())
                || !Objects.equals(dataset.getParsingOptions(), preparation.parsingOptions())
                || !Objects.equals(table.getUpdatedAt(), preparation.tableUpdatedAt())
                || table.getCurrentLoadJobId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "重新解析期间文件数据集或逻辑表已经变化，请重试");
        }
        List<FileDatasetTableSource> current = currentSources(table.getId());
        if (current.size() != preparation.sources().size() || parsedSources.size() != current.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "重新解析期间逻辑表的数据来源已经变化，请重试");
        }
        Map<UUID, FileDatasetFile> files = fileRepository.findAllById(
                        current.stream().map(FileDatasetTableSource::getSourceFileId).distinct().toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(FileDatasetFile::getId, file -> file));
        for (int index = 0; index < current.size(); index++) {
            FileDatasetTableSource source = current.get(index);
            SpatialSourceSnapshot expected = preparation.sources().get(index);
            FileDatasetFile file = files.get(source.getSourceFileId());
            if (!expected.matches(source, file)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "重新解析期间逻辑表的数据来源已经变化，请重试");
            }
        }

        fieldRepository.deleteByFileDatasetTableId(table.getId());
        FileDatasetParser.ParseResult canonical = parsedSources.getFirst();
        fieldRepository.saveAll(canonical.fields().stream().map(field -> FileDatasetField.create(
                table.getId(), field.name(), field.sortOrder(), field.type(), field.nullable()
        )).toList());
        for (int index = 0; index < current.size(); index++) {
            FileDatasetParser.ParseResult parsed = parsedSources.get(index);
            current.get(index).replaceSchema(
                    schemaValidator.fingerprint(parsed.fields()), writeSourceMetadata(parsed.sourceMetadata())
            );
        }
        sourceRepository.saveAll(current);
        boolean previewSupported = parsedSources.stream().allMatch(FileDatasetParser.ParseResult::previewSupported);
        table.replaceSpatialSchema(writeParsedMetadata(canonical), previewSupported, spatialReference);
        tableRepository.save(table);
        fieldRepository.flush();
        sourceRepository.flush();
        tableRepository.flush();
        return tableResponse(table);
    }

    private static void requireEffectiveSpatialReference(
            FileDatasetParser.ParseResult parsed,
            CrsReference expected
    ) {
        List<CrsReference> references = parsed.fields().stream()
                .filter(field -> field.type().type() == PlatformDataType.GEOMETRY)
                .map(field -> field.type().geometry().crs())
                .distinct()
                .toList();
        if (references.isEmpty()) {
            throw new FileDatasetParsingException("文件表未识别到 Geometry 字段");
        }
        if (references.size() != 1 || !references.getFirst().equals(expected)) {
            throw new FileDatasetParsingException(
                    "文件内声明的空间参考为 " + references.getFirst().authority() + ":"
                            + references.getFirst().code() + "，不能覆盖为 "
                            + expected.authority() + ":" + expected.code()
            );
        }
    }

    private List<FileDatasetTableSource> currentSources(UUID tableId) {
        return sourceRepository.findByFileDatasetTableIdOrderBySourceOrderAsc(tableId);
    }

    private List<FileDatasetTable> tablesForFile(UUID fileId) {
        Set<UUID> ids = sourceRepository.findBySourceFileIdOrderByCreatedAtAsc(fileId).stream()
                .map(FileDatasetTableSource::getFileDatasetTableId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        parseJobRepository.findBySourceFileIdAndStatusIn(fileId, NON_TERMINAL_JOB_STATUSES).stream()
                .map(FileDatasetParseJob::getFileDatasetTableId)
                .filter(Objects::nonNull)
                .forEach(ids::add);
        return tableRepository.findAllById(ids);
    }

    private static <T> PageResponse<T> page(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("事务未返回文件数据集处理结果");
        }
        return value;
    }

    private record UploadContext(FileDatasetType type, long existingFileCount) { }
    private record ReplacementContext(FileDatasetType type) { }
    private record UploadedFile(
            String fileName,
            String contentType,
            long sizeBytes,
            FileDatasetCompression compression,
            FileDatasetFormat format
    ) { }
    private record PreparedUpload(
            UploadedFile upload,
            String objectKey,
            String eTag,
            List<FileDatasetParser.DiscoveredTable> tables
    ) {
        private PreparedUpload withTables(List<FileDatasetParser.DiscoveredTable> discoveredTables) {
            return new PreparedUpload(upload, objectKey, eTag, List.copyOf(discoveredTables));
        }
    }
    private record PreviewPreparation(
            List<FileDatasetContentParser.Input> inputs,
            List<FileDatasetFieldResponse> fields
    ) { }
    private record SpatialReferencePreparation(
            UUID datasetId,
            Instant datasetUpdatedAt,
            String parsingOptions,
            UUID tableId,
            Instant tableUpdatedAt,
            List<SpatialSourceSnapshot> sources
    ) {
        private SpatialReferencePreparation {
            sources = List.copyOf(sources);
        }
    }
    private record SpatialSourceSnapshot(
            UUID sourceId,
            Instant sourceUpdatedAt,
            UUID sourceFileId,
            String schemaFingerprint,
            String sourceMetadata,
            Instant fileUpdatedAt,
            FileDatasetFileStatus fileStatus,
            String storageEtag,
            FileDatasetFormat format,
            FileDatasetCompression compression,
            String effectiveObjectKey,
            long sizeBytes,
            String parsingOptions,
            String sourceKey
    ) {
        private FileDatasetContentParser.Input input(int epsgCodeOverride) {
            return new FileDatasetContentParser.Input(
                    format, compression, effectiveObjectKey, sizeBytes,
                    parsingOptions, sourceKey, epsgCodeOverride
            );
        }

        private boolean matches(FileDatasetTableSource source, FileDatasetFile file) {
            if (file == null) {
                return false;
            }
            String currentObjectKey = file.getStorageKind() == FileDatasetStorageKind.SINGLE_OBJECT
                    ? file.getObjectKey() : file.getMaterializedPrefix();
            return sourceId.equals(source.getId())
                    && Objects.equals(sourceUpdatedAt, source.getUpdatedAt())
                    && sourceFileId.equals(source.getSourceFileId())
                    && Objects.equals(schemaFingerprint, source.getSchemaFingerprint())
                    && Objects.equals(sourceMetadata, source.getSourceMetadata())
                    && Objects.equals(fileUpdatedAt, file.getUpdatedAt())
                    && fileStatus == file.getStatus()
                    && Objects.equals(storageEtag, file.getStorageEtag())
                    && format == file.getFormat()
                    && compression == file.getCompression()
                    && Objects.equals(effectiveObjectKey, currentObjectKey);
        }
    }
    private record ContentPreparation(String fileName, String objectKey, String contentType, long sizeBytes) { }
}
