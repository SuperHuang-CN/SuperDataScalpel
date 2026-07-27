package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
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
import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;
import cn.superhuang.data.scalpel.shapefile.ShapefileRandomAccessObject;
import cn.superhuang.data.scalpel.shapefile.ShapefileSource;
import cn.superhuang.data.scalpel.shapefile.ShapefileSourceInfo;
import cn.superhuang.data.scalpel.shapefile.ShapefileSourceType;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
@SpringBootTest(properties = {
        "data-scalpel.file-parsing.shp.max-preview-geometry-points-per-feature=10",
        "data-scalpel.file-parsing.shp.max-preview-total-geometry-points=6"
})
@Import(FileDatasetShpIntegrationTests.StorageConfiguration.class)
class FileDatasetShpIntegrationTests {

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
    private ShpTestObjectStorage storage;

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
    void acceptsArchivesOneAtATimeAndBuildsIndependentSpatialTables() throws Exception {
        String datasetId = createShpDataset("城市 SHP", null, "GB18030");
        byte[] roads = roadsArchive(temporaryDirectory.resolve("roads"), "roads", "UTF-8");
        byte[] buildings = polygonArchive(temporaryDirectory.resolve("buildings"), "buildings");

        String upload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "roads.zip", "application/zip", roads)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].format").value("SHP"))
                .andExpect(jsonPath("$.files[0].storageKind").value("SHAPEFILE_COMPONENT_SET"))
                .andExpect(jsonPath("$.files[0].status").value("PREPARING"))
                .andExpect(jsonPath("$.tables").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String roadsFileId = JsonPath.read(upload, "$.files[0].id");
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "buildings.zip", "application/zip", buildings)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.files[0].status").value("PREPARING"));

        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("shp-prepare-1"));
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("shp-prepare-2"));
        List<FileDatasetTable> tables = tableRepository.findByFileDatasetIdOrderByCreatedAtAsc(
                UUID.fromString(datasetId)
        );
        assertEquals(2, tables.size());
        assertTrue(tables.stream().allMatch(table -> table.getParseStatus() == FileDatasetParseStatus.QUEUED));
        assertTrue(tableSourceRepository.findAll().isEmpty());
        assertTrue(storage.manifestCount() == 2);

        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("shp-table-1"));
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("shp-table-2"));
        assertEquals(List.of("buildings", "roads"), tableSourceRepository.findAll().stream()
                .map(source -> source.getSourceName()).sorted().toList());
        FileDatasetTable roadsTable = tablesForSourceFile(UUID.fromString(roadsFileId)).getFirst();
        String roadsTableId = roadsTable.getId().toString();

        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", datasetId, roadsTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("_geometry"))
                .andExpect(jsonPath("$.fields[1].fieldType").value("LONG"))
                .andExpect(jsonPath("$.fields[2].fieldType").value("BOOLEAN"))
                .andExpect(jsonPath("$.fields[3].fieldType").value("DATE"))
                .andExpect(jsonPath("$.fields[4].fieldType").value("DECIMAL"))
                .andExpect(jsonPath("$.fields[5].name").value("_geometry_1"))
                .andExpect(jsonPath("$.fields[5].fieldType").value("STRING"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, roadsTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceMetadata.shapeType").value("POINT_M"))
                .andExpect(jsonPath("$.sourceMetadata.dbfCharset").value("UTF-8"))
                .andExpect(jsonPath("$.sourceMetadata.dbfFields[0].name").value("_geometry"))
                .andExpect(jsonPath("$.sourceMetadata.dbfFields[0].length").value(20))
                .andExpect(jsonPath("$.sourceMetadata.geometryField").value("_geometry_1"))
                .andExpect(jsonPath("$.sourceMetadata.spatialReference.wkt").value("LOCAL_CS[\"fixture\"]"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", datasetId, roadsTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value("attribute-value"))
                .andExpect(jsonPath("$.rows[0][5].type").value("Point"))
                .andExpect(jsonPath("$.rows[0][5].hasM").value(true))
                .andExpect(jsonPath("$.rows[0][5].coordinates[0]").value(120.5))
                .andExpect(jsonPath("$.rows[0][5].coordinates[2]").value(nullValue()));

        var download = mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/files/{fileId}/content", datasetId, roadsFileId
                ))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(download))
                .andExpect(status().isOk())
                .andExpect(content().bytes(roads));
        mockMvc.perform(get("/api/v1/file-datasets/{id}", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(2))
                .andExpect(jsonPath("$.tableCount").value(2))
                .andExpect(jsonPath("$.readyTableCount").value(2));
    }

    @Test
    void rejectsMultipleComponentSetsAndDeletesTheTemporaryFile() throws Exception {
        String datasetId = createShpDataset("非法 SHP", null, "GB18030");
        byte[] invalid = twoComponentSetsArchive(temporaryDirectory.resolve("two-sets"));
        String upload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "invalid.zip", "application/zip", invalid)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(upload, "$.files[0].id");
        String objectKey = fileRepository.findById(UUID.fromString(fileId)).orElseThrow().getObjectKey();

        assertEquals(FileDatasetParseWorker.ExecutionOutcome.FAILED, worker.runOne("invalid-shp"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/files", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        assertFalse(fileRepository.existsById(UUID.fromString(fileId)));
        assertFalse(storage.contains(objectKey));
        assertEquals(0, storage.materializedObjectCount());

        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/files/{fileId}/actions/prepare", datasetId, fileId
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    void changingCharsetOptionsAfterFailureAllowsAFreshUpload() throws Exception {
        String datasetId = createShpDataset("编码 SHP", null, "GB18030");
        byte[] archive = roadsArchive(temporaryDirectory.resolve("encoding"), "encoded", " ");
        String upload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "encoded.zip", "application/zip", archive)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(upload, "$.files[0].id");
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.FAILED, worker.runOne("encoding-failure"));
        assertFalse(fileRepository.existsById(UUID.fromString(fileId)));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/update", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"编码 SHP",
                                  "parsingOptions":{
                                    "kind":"SHP",
                                    "dbfCharsetOverride":"UTF-8",
                                    "dbfFallbackCharset":"GB18030"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parsingOptions.dbfCharsetOverride").value("UTF-8"));
        String retriedUpload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "encoded.zip", "application/zip", archive)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String replacementFileId = JsonPath.read(retriedUpload, "$.files[0].id");

        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("encoding-prepare"));
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("encoding-table"));
        FileDatasetTable table = tablesForSourceFile(UUID.fromString(replacementFileId)).getFirst();
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, table.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sourceMetadata.dbfCharset").value("UTF-8"));
    }

    @Test
    void rejectsMultiPatchBeforePublishingATable() throws Exception {
        String datasetId = createShpDataset("MultiPatch", null, "GB18030");
        Path components = temporaryDirectory.resolve("multipatch");
        new TestShapefileBuilder(
                components,
                "multipatch",
                ShapefileShapeType.MULTIPATCH,
                new ShapefileEnvelope(0, 0, 1, 1, 0, 1, 0, 1)
        ).field("id", 'N', 4, 0)
                .record(TestShapefileBuilder.nullShape(), false, "1")
                .write();
        byte[] archive = zipComponents(components);
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "multipatch.zip", "application/zip", archive)))
                .andExpect(status().isCreated());

        assertEquals(FileDatasetParseWorker.ExecutionOutcome.FAILED, worker.runOne("multipatch-worker"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/files", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        assertTrue(tableRepository.findByFileDatasetIdOrderByCreatedAtAsc(
                UUID.fromString(datasetId)
        ).isEmpty());
    }

    @Test
    void keepsSchemaWhenGeometryExceedsThePreviewPointBudget() throws Exception {
        String datasetId = createShpDataset("超大几何 SHP", null, "GB18030");
        byte[] archive = oversizedPolygonArchive(temporaryDirectory.resolve("oversized"), "oversized");
        String upload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "oversized.zip", "application/zip", archive)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(upload, "$.files[0].id");

        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("oversized-prepare"));
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("oversized-table"));
        FileDatasetTable table = tablesForSourceFile(UUID.fromString(fileId)).getFirst();

        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, table.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("SCHEMA_READY"))
                .andExpect(jsonPath("$.previewSupported").value(false))
                .andExpect(jsonPath("$.sourceMetadata.previewUnavailableReason")
                        .value("空间几何超过预览安全上限，仅保留 Schema"))
                .andExpect(jsonPath("$.sourceMetadata.maxPreviewTotalGeometryPoints").value(6));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/schema", datasetId, table.getId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.fields[1].name").value("_geometry"));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/preview", datasetId, table.getId()
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("空间几何超过预览安全上限，仅保留 Schema"));
    }

    @Test
    void replacementKeepsLogicalTableAndSchemaStableUntilDatasetDeletion() throws Exception {
        String datasetId = createShpDataset("生命周期 SHP", null, "GB18030");
        byte[] roads = roadsArchive(temporaryDirectory.resolve("lifecycle-roads"), "roads", "UTF-8");
        String upload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(new MockMultipartFile("files", "roads.zip", "application/zip", roads)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID fileId = UUID.fromString(JsonPath.read(upload, "$.files[0].id"));
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("lifecycle-prepare-1"));
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("lifecycle-table-1"));

        FileDatasetFile originalFile = fileRepository.findById(fileId).orElseThrow();
        String originalObjectKey = originalFile.getObjectKey();
        String originalPrefix = originalFile.getMaterializedPrefix();
        FileDatasetTable originalTable = tablesForSourceFile(fileId).getFirst();
        UUID originalTableId = originalTable.getId();
        List<UUID> originalFieldIds = fieldRepository
                .findByFileDatasetTableIdOrderBySortOrderAsc(originalTableId)
                .stream().map(field -> field.getId()).toList();
        assertFalse(originalFieldIds.isEmpty());
        assertTrue(storage.contains(originalObjectKey));
        assertTrue(storage.containsPrefix(originalPrefix));

        byte[] replacement = roadsArchive(
                temporaryDirectory.resolve("lifecycle-roads-new"), "roads-new", "UTF-8"
        );
        String replacementSubmission = mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/replace-data",
                        datasetId, originalTableId
                ).file(new MockMultipartFile("file", "roads-new.zip", "application/zip", replacement)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.file.status").value("PREPARING"))
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.table.id").value(originalTableId.toString()))
                .andExpect(jsonPath("$.table.currentLoadJobId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID replacementFileId = UUID.fromString(JsonPath.read(replacementSubmission, "$.file.id"));
        assertNotEquals(fileId, replacementFileId);
        FileDatasetFile replacementFile = fileRepository.findById(replacementFileId).orElseThrow();
        String replacementObjectKey = replacementFile.getObjectKey();
        assertNotEquals(originalObjectKey, replacementObjectKey);
        assertTrue(storage.contains(originalObjectKey));
        assertTrue(storage.containsPrefix(originalPrefix));
        assertTrue(tableRepository.findById(originalTableId).isPresent());
        assertEquals(originalFieldIds, fieldRepository
                .findByFileDatasetTableIdOrderBySortOrderAsc(originalTableId)
                .stream().map(field -> field.getId()).toList());
        assertTrue(storage.contains(replacementObjectKey));

        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("lifecycle-prepare-2"));
        assertEquals(FileDatasetParseWorker.ExecutionOutcome.SUCCEEDED, worker.runOne("lifecycle-table-2"));
        FileDatasetFile preparedReplacement = fileRepository.findById(replacementFileId).orElseThrow();
        String replacementPrefix = preparedReplacement.getMaterializedPrefix();
        FileDatasetTable replacementTable = tableRepository.findById(originalTableId).orElseThrow();
        assertEquals(null, replacementTable.getCurrentLoadJobId());
        assertEquals(originalFieldIds, fieldRepository
                .findByFileDatasetTableIdOrderBySortOrderAsc(originalTableId)
                .stream().map(field -> field.getId()).toList());
        assertEquals(
                List.of(replacementFileId),
                tableSourceRepository.findByFileDatasetTableIdOrderBySourceOrderAsc(originalTableId)
                        .stream().map(source -> source.getSourceFileId()).toList()
        );
        assertTrue(storage.containsPrefix(replacementPrefix));
        assertFalse(storage.contains(originalObjectKey));
        assertFalse(storage.containsPrefix(originalPrefix));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/delete", datasetId))
                .andExpect(status().isNoContent());

        assertFalse(fileRepository.findById(fileId).isPresent());
        assertFalse(fileRepository.findById(replacementFileId).isPresent());
        assertFalse(tableRepository.findById(originalTableId).isPresent());
        assertTrue(fieldRepository.findByFileDatasetTableIdOrderBySortOrderAsc(replacementTable.getId()).isEmpty());
        assertFalse(storage.contains(originalObjectKey));
        assertFalse(storage.containsPrefix(originalPrefix));
        assertFalse(storage.contains(replacementObjectKey));
        assertFalse(storage.containsPrefix(replacementPrefix));
    }

    private String createShpDataset(String name, String override, String fallback) throws Exception {
        String overrideJson = override == null ? "null" : "\"" + override + "\"";
        String response = mockMvc.perform(post("/api/v1/file-datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"%s",
                                  "type":"SHP",
                                  "parsingOptions":{
                                    "kind":"SHP",
                                    "dbfCharsetOverride":%s,
                                    "dbfFallbackCharset":"%s"
                                  }
                                }
                                """.formatted(name, overrideJson, fallback)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SHP"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static byte[] roadsArchive(Path directory, String name, String cpg) throws IOException {
        new TestShapefileBuilder(
                directory,
                name,
                ShapefileShapeType.POINT_M,
                new ShapefileEnvelope(120, 30, 121, 31, 0, 0, -1.0e39, -1.0e39)
        ).field("_geometry", 'C', 20, 0)
                .field("population", 'N', 8, 0)
                .field("active", 'L', 1, 0)
                .field("created", 'D', 8, 0)
                .field("amount", 'N', 10, 2)
                .record(
                        TestShapefileBuilder.point(
                                ShapefileShapeType.POINT_M, 120.5, 30.5, null, -1.0e39
                        ),
                        false,
                        "attribute-value", "12", "T", "20260722", "12.50"
                )
                .cpg(cpg)
                .prj("LOCAL_CS[\"fixture\"]")
                .write();
        return zipComponents(directory);
    }

    private static byte[] polygonArchive(Path directory, String name) throws IOException {
        new TestShapefileBuilder(
                directory,
                name,
                ShapefileShapeType.POLYGON,
                new ShapefileEnvelope(0, 0, 1, 1, 0, 0, 0, 0)
        ).field("name", 'C', 20, 0)
                .record(TestShapefileBuilder.multipart(
                        ShapefileShapeType.POLYGON,
                        new int[]{5},
                        new double[]{0, 1, 1, 0, 0},
                        new double[]{0, 0, 1, 1, 0},
                        null,
                        null
                ), false, "Building A")
                .write();
        return zipComponents(directory);
    }

    private static byte[] oversizedPolygonArchive(Path directory, String name) throws IOException {
        new TestShapefileBuilder(
                directory,
                name,
                ShapefileShapeType.POLYGON,
                new ShapefileEnvelope(0, 0, 2, 2, 0, 0, 0, 0)
        ).field("name", 'C', 20, 0)
                .record(TestShapefileBuilder.multipart(
                        ShapefileShapeType.POLYGON,
                        new int[]{7},
                        new double[]{0, 1, 2, 2, 1, 0, 0},
                        new double[]{0, 0, 0, 1, 2, 1, 0},
                        null,
                        null
                ), false, "Oversized")
                .write();
        return zipComponents(directory);
    }

    private static byte[] twoComponentSetsArchive(Path directory) throws IOException {
        Path roads = Files.createDirectories(directory.resolve("roads"));
        Path lakes = Files.createDirectories(directory.resolve("lakes"));
        polygonArchive(roads, "roads");
        polygonArchive(lakes, "lakes");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addDirectory(zip, roads, "roads");
            addDirectory(zip, lakes, "lakes");
        }
        return output.toByteArray();
    }

    private static byte[] zipComponents(Path directory) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            addDirectory(zip, directory, "wrapper");
        }
        return output.toByteArray();
    }

    private static void addDirectory(ZipOutputStream zip, Path directory, String archiveDirectory) throws IOException {
        List<Path> files;
        try (var paths = Files.list(directory)) {
            files = paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        for (Path file : files) {
            zip.putNextEntry(new ZipEntry(archiveDirectory + "/" + file.getFileName()));
            Files.copy(file, zip);
            zip.closeEntry();
        }
    }

    private List<FileDatasetTable> tablesForSourceFile(UUID sourceFileId) {
        return tableSourceRepository.findBySourceFileIdOrderByCreatedAtAsc(sourceFileId).stream()
                .map(source -> tableRepository.findById(source.getFileDatasetTableId()).orElseThrow())
                .toList();
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
        ShpTestObjectStorage shpTestObjectStorage() {
            return new ShpTestObjectStorage();
        }
    }

    static final class ShpTestObjectStorage implements FileObjectStorage {
        private final Map<String, StoredValue> values = new ConcurrentHashMap<>();

        @Override
        public StoredFileObject store(
                String objectKey,
                InputStream inputStream,
                long contentLength,
                String contentType
        ) {
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
            values.keySet().removeIf(key -> key.startsWith(prefix + "/"));
        }

        @Override
        public ShapefileDataset openShapefile(
                String prefix,
                Set<ShapefileComponent> components,
                ShapefileOpenOptions options
        ) {
            EnumMap<ShapefileComponent, byte[]> content = new EnumMap<>(ShapefileComponent.class);
            for (ShapefileComponent component : components) {
                StoredValue value = values.get(prefix + "/data." + component.extension());
                if (value != null) {
                    content.put(component, value.bytes());
                }
            }
            return ShapefileDataset.open(new InMemoryShapefileSource(prefix, content), options);
        }

        int manifestCount() {
            return (int) values.keySet().stream().filter(key -> key.endsWith("/manifest.json")).count();
        }

        int materializedObjectCount() {
            return (int) values.keySet().stream()
                    .filter(key -> key.startsWith("file-datasets/materialized/"))
                    .count();
        }

        boolean contains(String objectKey) {
            return values.containsKey(objectKey);
        }

        boolean containsPrefix(String prefix) {
            return prefix != null && values.keySet().stream().anyMatch(key -> key.startsWith(prefix + "/"));
        }

        void clear() {
            values.clear();
        }

        private record StoredValue(byte[] bytes, String contentType) {
        }
    }

    private static final class InMemoryShapefileSource implements ShapefileSource {
        private final ShapefileSourceInfo info;
        private final Map<ShapefileComponent, byte[]> components;
        private boolean closed;

        private InMemoryShapefileSource(String prefix, Map<ShapefileComponent, byte[]> components) {
            this.info = new ShapefileSourceInfo(ShapefileSourceType.LOCAL, "memory:" + prefix);
            this.components = Map.copyOf(components);
        }

        @Override
        public ShapefileSourceInfo info() {
            ensureOpen();
            return info;
        }

        @Override
        public boolean exists(ShapefileComponent component) {
            ensureOpen();
            return components.containsKey(component);
        }

        @Override
        public ShapefileRandomAccessObject open(ShapefileComponent component) {
            ensureOpen();
            byte[] content = components.get(component);
            if (content == null) {
                throw new ShapefileException(
                        ShapefileErrorCode.MISSING_COMPONENT,
                        "Missing fixture component " + component
                );
            }
            return new InMemoryRandomAccessObject(component.name(), content);
        }

        @Override
        public void close() {
            closed = true;
        }

        private void ensureOpen() {
            if (closed) {
                throw new ShapefileException(ShapefileErrorCode.CLOSED, "Fixture source is closed");
            }
        }
    }

    private static final class InMemoryRandomAccessObject implements ShapefileRandomAccessObject {
        private final String name;
        private final byte[] content;
        private boolean closed;

        private InMemoryRandomAccessObject(String name, byte[] content) {
            this.name = name;
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
                throw new ShapefileException(ShapefileErrorCode.INVALID_OFFSET, "Invalid offset for " + name);
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
                throw new ShapefileException(ShapefileErrorCode.CLOSED, name + " is closed");
            }
        }
    }
}
