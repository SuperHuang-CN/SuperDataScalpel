package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobType;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableSourceRepository;
import cn.superhuang.data.scalpel.business.filedataset.service.queue.FileDatasetParseWorker;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageObjectNotFoundException;
import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.filegdb.FileGdbRandomAccessObject;
import cn.superhuang.data.scalpel.filegdb.FileGdbSource;
import cn.superhuang.data.scalpel.filegdb.FileGdbSourceInfo;
import cn.superhuang.data.scalpel.filegdb.FileGdbSourceType;
import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Import(FileDatasetGdbIntegrationTests.StorageConfiguration.class)
@TestPropertySource(properties = "data-scalpel.file-parsing.gdb.max-entries=12")
class FileDatasetGdbIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;
    @Autowired
    private FileDatasetRepository datasetRepository;
    @Autowired
    private FileDatasetFileRepository fileRepository;
    @Autowired
    private FileDatasetTableRepository tableRepository;
    @Autowired
    private FileDatasetTableSourceRepository tableSourceRepository;
    @Autowired
    private FileDatasetFieldRepository fieldRepository;
    @Autowired
    private FileDatasetParseJobRepository jobRepository;
    @Autowired
    private DirectoryRepository directoryRepository;
    @Autowired
    private FileDatasetParseWorker worker;
    @Autowired
    private GdbTestObjectStorage storage;

    @TempDir
    private Path temporaryDirectory;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        String token = loginAsAdministrator(mockMvc);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .apply(springSecurity())
                .build();
        clearData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void preparesOneGdbZipDiscoversLayersAndParsesSpatialPreview() throws Exception {
        String datasetId = createGdbDataset("城市空间数据");
        TestArchive fixture = validArchive(temporaryDirectory);
        byte[] archive = fixture.bytes();

        String upload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "city.gdb.zip", "application/zip", archive)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files[0].format").value("GDB"))
                .andExpect(jsonPath("$.files[0].compression").value("ZIP"))
                .andExpect(jsonPath("$.files[0].storageKind").value("GDB_DIRECTORY"))
                .andExpect(jsonPath("$.files[0].status").value("PREPARING"))
                .andExpect(jsonPath("$.files[0].currentPreparationJobId").isNotEmpty())
                .andExpect(jsonPath("$.tables").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(upload, "$.files[0].id");

        assertEquals(FileDatasetParseJobType.FILE_PREPARATION, jobRepository.findAll().getFirst().getType());
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("gdb-prepare-worker"));

        mockMvc.perform(get("/api/v1/file-datasets/{id}/files", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("READY"))
                .andExpect(jsonPath("$.content[0].materializedEntryCount").value(fixture.entryCount()))
                .andExpect(jsonPath("$.content[0].materializedSizeBytes").isNumber());

        List<FileDatasetTable> tables = tablesForDataset(datasetId);
        assertTrue(tableSourceRepository.findBySourceFileIdOrderByCreatedAtAsc(UUID.fromString(fileId)).isEmpty());
        assertTrue(tables.stream().allMatch(table -> table.getParseStatus() == FileDatasetParseStatus.QUEUED));

        for (int index = 0; index < tables.size(); index++) {
            assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("gdb-table-worker-" + index));
        }
        tables = tablesForSourceFile(UUID.fromString(fileId));
        assertEquals(
                List.of("ScalarPointXY", "PointXYZ", "PointXYM", "PointXYZM"),
                tableSourceRepository.findBySourceFileIdOrderByCreatedAtAsc(UUID.fromString(fileId))
                        .stream().map(source -> source.getSourceName()).toList()
        );
        assertEquals(FileDatasetParseStatus.READY, tables.get(0).getParseStatus());
        assertEquals(FileDatasetParseStatus.READY, tables.get(1).getParseStatus());
        assertEquals(FileDatasetParseStatus.READY, tables.get(2).getParseStatus());
        assertEquals(FileDatasetParseStatus.SCHEMA_READY, tables.get(3).getParseStatus());

        String citiesTableId = tables.getFirst().getId().toString();
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", datasetId, citiesTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[1].name").value("Shape"))
                .andExpect(jsonPath("$.fields[1].fieldType").value("STRING"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, citiesTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewSupported").value(true))
                .andExpect(jsonPath("$.sourceMetadata.layerType").value("POINT"))
                .andExpect(jsonPath("$.sourceMetadata.spatialReference.hasZ").value(false));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", datasetId, citiesTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value(1))
                .andExpect(jsonPath("$.rows[0][1].type").value("Point"))
                .andExpect(jsonPath("$.rows[0][1].coordinates[0]").value(12.25));

        String unsupportedId = tables.get(3).getId().toString();
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", datasetId, unsupportedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(2));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", datasetId, unsupportedId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("该 GDB 图层仅支持 Schema，暂不支持数据预览"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableCount").value(4))
                .andExpect(jsonPath("$.readyTableCount").value(4));

        var download = mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/files/{fileId}/content", datasetId, fileId
                ))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(download))
                .andExpect(status().isOk())
                .andExpect(content().bytes(archive));
        assertTrue(storage.containsMaterializedPrefix());

        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "second.zip", "application/zip", archive)))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsUnsafeArchiveDuringBackgroundPreparation() throws Exception {
        String datasetId = createGdbDataset("不安全 GDB");
        byte[] archive = zip(Map.of("../unsafe.gdb/gdb", "bad".getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "unsafe.zip", "application/zip", archive)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files[0].status").value("PREPARING"));

        assertEquals(FileDatasetParseWorker.ExecutionOutcome.FAILED, worker.runOne("unsafe-gdb-worker"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/files", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        assertPreparationFailed(datasetId, "ZIP 条目包含不安全路径");
        assertTrue(storage.isEmpty());
    }

    @Test
    void appliesTheEntryLimitToDirectoriesAndRejectsWindowsAbsolutePaths() throws Exception {
        String directoryBombDatasetId = createGdbDataset("目录条目过多");
        ByteArrayOutputStream directoryBomb = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(directoryBomb)) {
            for (int index = 0; index < 13; index++) {
                zip.putNextEntry(new ZipEntry("wrapper-" + index + "/"));
                zip.closeEntry();
            }
        }
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", directoryBombDatasetId)
                        .file(new MockMultipartFile(
                                "files", "directories.zip", "application/zip", directoryBomb.toByteArray()
                        )))
                .andExpect(status().isCreated());
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.FAILED, worker.runOne("directory-limit-worker"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/files", directoryBombDatasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        assertPreparationFailed(directoryBombDatasetId, "ZIP 条目数量超过允许上限");
        assertTrue(storage.isEmpty());

        String absolutePathDatasetId = createGdbDataset("绝对路径 GDB");
        byte[] absolutePath = zip(Map.of("C:/unsafe.gdb/gdb", new byte[] {0, 0, 0, 0}));
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", absolutePathDatasetId)
                        .file(new MockMultipartFile("files", "absolute.zip", "application/zip", absolutePath)))
                .andExpect(status().isCreated());
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.FAILED, worker.runOne("absolute-path-worker"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/files", absolutePathDatasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        assertPreparationFailed(absolutePathDatasetId, "ZIP 条目路径无效");
        assertTrue(storage.isEmpty());
    }

    @Test
    void replacingAndDeletingAGdbCleansOwnedObjectsTablesAndSchemas() throws Exception {
        Path firstParent = Files.createDirectory(temporaryDirectory.resolve("first"));
        Path replacementParent = Files.createDirectory(temporaryDirectory.resolve("replacement"));
        TestArchive firstArchive = validArchive(firstParent);
        TestArchive replacementArchive = validArchive(replacementParent);
        String datasetId = createGdbDataset("可替换 GDB");

        String upload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile(
                                "files", "first.zip", "application/zip", firstArchive.bytes()
                        )))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID fileId = UUID.fromString(JsonPath.read(upload, "$.files[0].id"));
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("initial-prepare-worker"));
        var original = fileRepository.findById(fileId).orElseThrow();
        String oldRawObjectKey = original.getObjectKey();
        String oldMaterializedPrefix = original.getMaterializedPrefix();
        List<UUID> oldTableIds = tablesForDataset(datasetId).stream()
                .map(FileDatasetTable::getId)
                .toList();
        assertTrue(storage.containsObject(oldRawObjectKey));
        assertTrue(storage.containsPrefix(oldMaterializedPrefix));

        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files/{fileId}/actions/replace", datasetId, fileId)
                        .file(new MockMultipartFile(
                                "file", "replacement.zip", "application/zip", replacementArchive.bytes()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[0].status").value("PREPARING"))
                .andExpect(jsonPath("$.tables").isEmpty());

        assertFalse(storage.containsObject(oldRawObjectKey));
        assertFalse(storage.containsPrefix(oldMaterializedPrefix));
        assertTrue(oldTableIds.stream().noneMatch(tableRepository::existsById));
        assertTrue(oldTableIds.stream().allMatch(id -> fieldRepository
                .findByFileDatasetTableIdOrderBySortOrderAsc(id).isEmpty()));
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("replacement-prepare-worker"));
        assertEquals(4, tablesForDataset(datasetId).size());

        mockMvc.perform(post("/api/v1/file-datasets/{id}/files/{fileId}/actions/delete", datasetId, fileId))
                .andExpect(status().isNoContent());
        assertTrue(storage.isEmpty());
        assertFalse(fileRepository.existsById(fileId));
        assertTrue(tablesForDataset(datasetId).isEmpty());
    }

    private String createGdbDataset(String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/file-datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","type":"GDB","parsingOptions":{"kind":"GDB"}}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("GDB"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static TestArchive validArchive(Path parent) throws IOException {
        TestFileGdbBuilder.Fixture fixture = TestFileGdbBuilder.scalarAndPoints(parent);
        try (FileChannel table = FileChannel.open(
                fixture.directory().resolve(fixture.layerId("PointXYZM") + ".gdbtable"),
                StandardOpenOption.WRITE
        )) {
            table.write(ByteBuffer.wrap(new byte[] {9}), 48);
        }
        List<Path> files;
        try (var paths = Files.list(fixture.directory())) {
            files = paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Path file : files) {
                zip.putNextEntry(new ZipEntry(
                        "wrapper/" + fixture.directory().getFileName() + "/" + file.getFileName()
                ));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
        return new TestArchive(output.toByteArray(), files.size());
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private List<FileDatasetTable> tablesForSourceFile(UUID sourceFileId) {
        return tableSourceRepository.findBySourceFileIdOrderByCreatedAtAsc(sourceFileId).stream()
                .map(source -> tableRepository.findById(source.getFileDatasetTableId()).orElseThrow())
                .toList();
    }

    private List<FileDatasetTable> tablesForDataset(String datasetId) {
        return tableRepository.findByFileDatasetIdOrderByCreatedAtAsc(UUID.fromString(datasetId));
    }

    private void assertPreparationFailed(String datasetId, String errorMessage) {
        var failed = jobRepository.findAll().stream()
                .filter(job -> UUID.fromString(datasetId).equals(job.getFileDatasetId()))
                .filter(job -> job.getType() == FileDatasetParseJobType.FILE_PREPARATION)
                .findFirst()
                .orElseThrow();
        assertEquals(FileDatasetParseJobStatus.FAILED, failed.getStatus());
        assertEquals(errorMessage, failed.getErrorMessage());
    }

    private void clearData() {
        fieldRepository.deleteAll();
        jobRepository.deleteAll();
        tableSourceRepository.deleteAll();
        tableRepository.deleteAll();
        fileRepository.deleteAll();
        datasetRepository.deleteAll();
        directoryRepository.deleteAll();
        storage.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageConfiguration {
        @Bean
        GdbTestObjectStorage gdbTestObjectStorage() {
            return new GdbTestObjectStorage();
        }
    }

    static class GdbTestObjectStorage implements FileObjectStorage {
        private final Map<String, StoredValue> values = new ConcurrentHashMap<>();

        @Override
        public StoredFileObject store(String objectKey, InputStream inputStream, long contentLength, String contentType) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            try {
                byte[] bytes = inputStream.readAllBytes();
                assertEquals(contentLength, bytes.length);
                values.put(objectKey, new StoredValue(bytes, contentType));
                return new StoredFileObject("etag-" + objectKey);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public FileObjectContent open(String objectKey) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            StoredValue value = values.get(objectKey);
            if (value == null) {
                throw new FileStorageObjectNotFoundException("对象不存在", null);
            }
            return new FileObjectContent(
                    new ByteArrayInputStream(value.bytes()), value.bytes().length, value.contentType()
            );
        }

        @Override
        public void delete(String objectKey) {
            values.remove(objectKey);
        }

        @Override
        public void deletePrefix(String prefix) {
            String normalized = prefix + "/";
            values.keySet().removeIf(key -> key.startsWith(normalized));
        }

        @Override
        public FileGeodatabase openFileGeodatabase(String prefix) {
            String normalizedPrefix = prefix + "/";
            Map<String, byte[]> files = values.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(normalizedPrefix))
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            entry -> entry.getKey().substring(normalizedPrefix.length()),
                            entry -> entry.getValue().bytes()
                    ));
            if (!files.containsKey("gdb")) {
                throw new FileStorageObjectNotFoundException("GDB marker 不存在", null);
            }
            return FileGeodatabase.open(new InMemoryFileGdbSource(prefix, files));
        }

        boolean containsMaterializedPrefix() {
            return values.keySet().stream().anyMatch(key -> key.startsWith("file-datasets/materialized/"));
        }

        boolean containsObject(String objectKey) {
            return values.containsKey(objectKey);
        }

        boolean containsPrefix(String prefix) {
            return values.keySet().stream().anyMatch(key -> key.startsWith(prefix + "/"));
        }

        boolean isEmpty() {
            return values.isEmpty();
        }

        void clear() {
            values.clear();
        }

        private record StoredValue(byte[] bytes, String contentType) {
        }
    }

    private static final class InMemoryFileGdbSource implements FileGdbSource {
        private final FileGdbSourceInfo info;
        private final Map<String, byte[]> files;
        private boolean closed;

        private InMemoryFileGdbSource(String prefix, Map<String, byte[]> files) {
            this.info = new FileGdbSourceInfo(FileGdbSourceType.LOCAL, "memory:" + prefix);
            this.files = Map.copyOf(files);
        }

        @Override
        public FileGdbSourceInfo info() {
            ensureOpen();
            return info;
        }

        @Override
        public boolean exists(String fileName) {
            ensureOpen();
            return files.containsKey(fileName);
        }

        @Override
        public FileGdbRandomAccessObject open(String fileName) {
            ensureOpen();
            byte[] content = files.get(fileName);
            if (content == null) {
                throw new FileGdbException(FileGdbErrorCode.MISSING_FILE, "Missing fixture file " + fileName);
            }
            return new InMemoryRandomAccessObject(fileName, content);
        }

        @Override
        public void close() {
            closed = true;
        }

        private void ensureOpen() {
            if (closed) {
                throw new FileGdbException(FileGdbErrorCode.CLOSED, "Fixture source is closed");
            }
        }
    }

    private static final class InMemoryRandomAccessObject implements FileGdbRandomAccessObject {
        private final String fileName;
        private final byte[] content;
        private boolean closed;

        private InMemoryRandomAccessObject(String fileName, byte[] content) {
            this.fileName = fileName;
            this.content = content;
        }

        @Override
        public long size() {
            ensureOpen();
            return content.length;
        }

        @Override
        public int read(long position, ByteBuffer target) {
            ensureOpen();
            if (position < 0 || position > Integer.MAX_VALUE) {
                throw new FileGdbException(FileGdbErrorCode.INVALID_OFFSET, "Invalid fixture offset for " + fileName);
            }
            if (!target.hasRemaining()) {
                return 0;
            }
            if (position >= content.length) {
                return -1;
            }
            int count = Math.min(target.remaining(), content.length - (int) position);
            target.put(content, (int) position, count);
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }

        private void ensureOpen() {
            if (closed) {
                throw new FileGdbException(FileGdbErrorCode.CLOSED, fileName + " is closed");
            }
        }
    }

    private record TestArchive(byte[] bytes, int entryCount) {
    }
}
