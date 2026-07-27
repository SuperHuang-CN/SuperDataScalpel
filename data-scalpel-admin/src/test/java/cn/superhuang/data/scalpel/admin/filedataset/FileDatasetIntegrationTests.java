package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetCompression;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseJobStatus;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetParseJobRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableSourceRepository;
import cn.superhuang.data.scalpel.business.filedataset.service.queue.FileDatasetParseJobCoordinator;
import cn.superhuang.data.scalpel.business.filedataset.service.queue.FileDatasetParseWorker;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageObjectNotFoundException;
import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.repository.SystemConfigurationRepository;
import com.jayway.jsonpath.JsonPath;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@Import(FileDatasetIntegrationTests.StorageConfiguration.class)
class FileDatasetIntegrationTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private FileDatasetRepository fileDatasetRepository;

    @Autowired
    private FileDatasetFileRepository fileDatasetFileRepository;

    @Autowired
    private FileDatasetTableRepository fileDatasetTableRepository;

    @Autowired
    private FileDatasetTableSourceRepository fileDatasetTableSourceRepository;

    @Autowired
    private FileDatasetFieldRepository fileDatasetFieldRepository;

    @Autowired
    private FileDatasetParseJobRepository fileDatasetParseJobRepository;

    @Autowired
    private FileDatasetParseJobCoordinator parseJobCoordinator;

    @Autowired
    private FileDatasetParseWorker parseWorker;

    @Autowired
    private SystemConfigurationRepository systemConfigurationRepository;

    @Autowired
    private DirectoryRepository directoryRepository;

    @Autowired
    private InMemoryFileObjectStorage fileObjectStorage;

    private MockMvc mockMvc;
    private final Map<String, String> fileIds = new ConcurrentHashMap<>();
    private final Map<String, String> tableIds = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .apply(springSecurity())
                .build();
        String accessToken = loginAsAdministrator(mockMvc);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .defaultRequest(get("/").header("Authorization", "Bearer " + accessToken))
                .apply(springSecurity())
                .build();
        clearData();
    }

    @AfterEach
    void tearDown() {
        clearData();
    }

    @Test
    void keepsLogicalTableAndSchemaStableWhenReplacingAllData() throws Exception {
        String directoryId = createDirectory("文件资料");
        byte[] firstContent = "id,name\n1,Old Road\n".getBytes(StandardCharsets.UTF_8);
        String created = mockMvc.perform(post("/api/v1/file-datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"道路数据", "directoryId":"%s", "type":"CSV",
                                  "parsingOptions":%s,
                                  "description":"原始道路数据"
                                }
                                """.formatted(directoryId, defaultOptions("CSV"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("道路数据"))
                .andExpect(jsonPath("$.directoryId").value(directoryId))
                .andExpect(jsonPath("$.type").value("CSV"))
                .andExpect(jsonPath("$.fileCount").value(0))
                .andExpect(jsonPath("$.tableCount").value(0))
                .andReturn().getResponse().getContentAsString();
        String datasetId = JsonPath.read(created, "$.id");

        String uploaded = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(filesPart("roads.csv", "text/csv", firstContent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files[0].originalFileName").value("roads.csv"))
                .andExpect(jsonPath("$.files[0].sizeBytes").value(firstContent.length))
                .andExpect(jsonPath("$.tables[0].parseStatus").value("QUEUED"))
                .andExpect(jsonPath("$.tables[0].currentLoadJobId").isNotEmpty())
                .andExpect(jsonPath("$.jobIds.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(uploaded, "$.files[0].id");
        String tableId = JsonPath.read(uploaded, "$.tables[0].id");
        fileIds.put(datasetId, fileId);
        tableIds.put(datasetId, tableId);

        mockMvc.perform(get("/api/v1/file-datasets")
                        .param("search", "name:*\"道路\"* AND type:\"CSV\"")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(datasetId));

        downloadContent(datasetId)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(content().bytes(firstContent));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/update", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"道路数据（更新）", "directoryId":"%s",
                                  "parsingOptions":%s, "description":"更新说明"
                                }
                                """.formatted(directoryId, defaultOptions("CSV"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("道路数据（更新）"))
                .andExpect(jsonPath("$.description").value("更新说明"));

        parseTableAndGet(datasetId, tableId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.currentLoadJobId").doesNotExist())
                .andExpect(jsonPath("$.sourceCount").value(1));
        List<UUID> fieldIds = fileDatasetFieldRepository
                .findByFileDatasetTableIdOrderBySortOrderAsc(UUID.fromString(tableId))
                .stream().map(field -> field.getId()).toList();
        org.junit.jupiter.api.Assertions.assertEquals(2, fieldIds.size());

        byte[] replacementContent = "id,name\n2,New Road\n".getBytes(StandardCharsets.UTF_8);
        String replaced = mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/replace-data",
                        datasetId, tableId
                ).file(filePart("roads-new.csv", "text/csv", replacementContent)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.file.format").value("CSV"))
                .andExpect(jsonPath("$.file.originalFileName").value("roads-new.csv"))
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.table.id").value(tableId))
                .andExpect(jsonPath("$.table.currentLoadJobId").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.blankOrNullString())
                ))
                .andReturn().getResponse().getContentAsString();
        String replacementFileId = JsonPath.read(replaced, "$.file.id");
        runUntilTableIsReady(tableId);
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCount").value(1))
                .andExpect(jsonPath("$.totalRowCount").value(1));
        org.junit.jupiter.api.Assertions.assertEquals(
                fieldIds,
                fileDatasetFieldRepository.findByFileDatasetTableIdOrderBySortOrderAsc(UUID.fromString(tableId))
                        .stream().map(field -> field.getId()).toList()
        );
        MvcResult replacementDownload = mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/files/{fileId}/content", datasetId, replacementFileId
                ))
                .andExpect(request().asyncStarted())
                .andReturn();
        replacementDownload.getAsyncResult();
        mockMvc.perform(asyncDispatch(replacementDownload))
                .andExpect(status().isOk())
                .andExpect(content().bytes(replacementContent));
        org.junit.jupiter.api.Assertions.assertEquals(
                1, fileObjectStorage.size(), "覆盖成功后旧对象必须立即删除"
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(replacementFileId),
                fileDatasetTableSourceRepository
                        .findByFileDatasetTableIdOrderBySourceOrderAsc(UUID.fromString(tableId))
                        .stream().map(source -> source.getSourceFileId().toString()).toList()
        );

        updateParsingOptions(datasetId, """
                {"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"LF",
                "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "文件数据集已经包含文件、表或解析任务，解析参数已经锁定"
                ));

        mockMvc.perform(get("/api/v1/directories").param("scope", "FILE_DATASET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].directResourceCount").value(1))
                .andExpect(jsonPath("$[0].resourceCount").value(1));
        mockMvc.perform(post("/api/v1/directories/{id}/actions/delete", directoryId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("目录包含业务数据，不能删除"));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/delete", datasetId))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.isEmpty());
        mockMvc.perform(get("/api/v1/file-datasets/{id}", datasetId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/directories/{id}/actions/delete", directoryId))
                .andExpect(status().isNoContent());
    }

    @Test
    void validatesDeclaredFormatAgainstTheUploadedOrStoredFileName() throws Exception {
        String datasetId = createEmptyFileDataset("错误格式", "PARQUET");
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(filesPart("roads.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("PARQUET 类型的文件扩展名不匹配"));
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.isEmpty());

        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(filesPart(
                                "invalid.parquet", "application/vnd.apache.parquet",
                                "not-a-parquet-container".getBytes(StandardCharsets.UTF_8)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("PARQUET 文件头无效"));
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.isEmpty());

        String excelDatasetId = createEmptyFileDataset("错误 Excel", "XLSX");
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", excelDatasetId)
                        .file(filesPart(
                                "invalid.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "not-an-xlsx-container".getBytes(StandardCharsets.UTF_8)
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("XLSX 文件头无效"));
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.isEmpty());

        String atomicDatasetId = createEmptyFileDataset("批量原子性", "CSV");
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", atomicDatasetId)
                        .file(filesPart("valid.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8)))
                        .file(filesPart("invalid.parquet", "application/octet-stream", "PAR1".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("每次只能上传一个文件"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}", atomicDatasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(0))
                .andExpect(jsonPath("$.tableCount").value(0));
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.isEmpty());

        String csvId = createFileDataset(
                "文本数据", "CSV", "roads.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/update", csvId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"文本数据\",\"parsingOptions\":{\"kind\":\"PARQUET\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("解析参数类型与文件数据集类型不匹配"));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/update", csvId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "文本数据",
                                  "parsingOptions": {
                                    "kind": "CSV", "charset": "NOT-A-CHARSET", "fieldDelimiter": ",",
                                    "recordDelimiter": "AUTO", "quoteCharacter": "\\\"",
                                    "escapeCharacter": "\\\\", "firstRowHeader": true
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("不支持的文件编码：NOT-A-CHARSET"));
    }

    @Test
    void compensatesStorageAndPersistenceFailuresAndDeletesReplacedObjectsImmediately() throws Exception {
        String storageFailureDatasetId = createEmptyFileDataset("存储失败", "CSV");
        fileObjectStorage.failNextStore();
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", storageFailureDatasetId)
                        .file(filesPart(
                                "roads.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8)
                        )))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("文件对象存储不可用"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}", storageFailureDatasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(0))
                .andExpect(jsonPath("$.tableCount").value(0));
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.isEmpty());

        String persistenceFailureDatasetId = createEmptyFileDataset("落库失败", "CSV");
        fileObjectStorage.afterNextStore(() -> fileDatasetRepository.deleteById(
                UUID.fromString(persistenceFailureDatasetId)
        ));
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", persistenceFailureDatasetId)
                        .file(filesPart(
                                "roads.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8)
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("文件数据集不存在"));
        org.junit.jupiter.api.Assertions.assertFalse(fileDatasetRepository.existsById(
                UUID.fromString(persistenceFailureDatasetId)
        ));
        org.junit.jupiter.api.Assertions.assertTrue(fileDatasetParseJobRepository.findAll().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.isEmpty());

        String replacementDatasetId = createFileDataset(
                "延期清理旧对象", "CSV", "roads.csv", "text/csv",
                "id,name\n1,Old Road\n".getBytes(StandardCharsets.UTF_8)
        );
        String replacementTableId = tableId(replacementDatasetId);
        runUntilTableIsReady(replacementTableId);
        byte[] replacement = "id,name\n2,New Road\n".getBytes(StandardCharsets.UTF_8);
        String replaced = mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/replace-data",
                        replacementDatasetId, replacementTableId
                ).file(filePart("roads-new.csv", "text/csv", replacement)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.file.originalFileName").value("roads-new.csv"))
                .andReturn().getResponse().getContentAsString();
        runUntilTableIsReady(replacementTableId);
        String replacementFileId = JsonPath.read(replaced, "$.file.id");
        MvcResult download = mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/files/{fileId}/content",
                        replacementDatasetId, replacementFileId
                ))
                .andExpect(request().asyncStarted())
                .andReturn();
        download.getAsyncResult();
        mockMvc.perform(asyncDispatch(download))
                .andExpect(status().isOk())
                .andExpect(content().bytes(replacement));
        org.junit.jupiter.api.Assertions.assertEquals(
                1, fileObjectStorage.size(), "覆盖成功后旧来源对象应立即删除"
        );
    }

    @Test
    void uploadsSingleTableFilesSeparatelyAndKeepsIndependentSchemas() throws Exception {
        String datasetId = createEmptyFileDataset("多文件 CSV", "CSV");
        String roadsUpload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(filesPart(
                                "roads.csv", "text/csv",
                                "id,name\n1,South Road\n".getBytes(StandardCharsets.UTF_8)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.tables.length()").value(1))
                .andExpect(jsonPath("$.tables[0].name").value("roads"))
                .andReturn().getResponse().getContentAsString();
        String roadsTableId = JsonPath.read(roadsUpload, "$.tables[0].id");
        parseTableAndGet(datasetId, roadsTableId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"));

        String districtsUpload = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(filesPart(
                                "districts.csv", "text/csv",
                                "code,active,amount\nA,true,12.5\n".getBytes(StandardCharsets.UTF_8)
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.files.length()").value(1))
                .andExpect(jsonPath("$.tables.length()").value(1))
                .andExpect(jsonPath("$.tables[0].name").value("districts"))
                .andReturn().getResponse().getContentAsString();
        String districtsTableId = JsonPath.read(districtsUpload, "$.tables[0].id");
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", datasetId, roadsTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("id"))
                .andExpect(jsonPath("$.fields[1].name").value("name"));
        parseTableAndGet(datasetId, districtsTableId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", datasetId, districtsTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("code"))
                .andExpect(jsonPath("$.fields[2].fieldType").value("DECIMAL"));

        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", datasetId, roadsTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableId").value(roadsTableId))
                .andExpect(jsonPath("$.fields.length()").value(2));
        mockMvc.perform(get("/api/v1/file-datasets/{id}", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileCount").value(2))
                .andExpect(jsonPath("$.tableCount").value(2))
                .andExpect(jsonPath("$.readyTableCount").value(2));

        updateParsingOptions(datasetId, """
                {"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"LF",
                "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "文件数据集已经包含文件、表或解析任务，解析参数已经锁定"
                ));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables", datasetId).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].parseStatus", containsInAnyOrder("READY", "READY")));
        org.junit.jupiter.api.Assertions.assertEquals(5, fileDatasetFieldRepository.findAll().size());
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", datasetId, roadsTableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(2));

        String otherDatasetId = createFileDataset(
                "其他 CSV", "CSV", "other.csv", "text/csv",
                "other_id\n9\n".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", otherDatasetId, roadsTableId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/files/{fileId}/content",
                        datasetId, fileId(otherDatasetId)
                ))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/files/{fileId}/actions/delete",
                        datasetId, fileId(otherDatasetId)
                ))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/file-datasets/{id}/files", datasetId)
                        .param("search", "id:\"%s\"".formatted(fileId(otherDatasetId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables", datasetId)
                        .param("search", "id:\"%s\"".formatted(tableId(otherDatasetId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void appendsReplacesAndDeletesActiveSourcesInDeterministicOrder() throws Exception {
        String datasetId = createFileDataset(
                "道路增量", "CSV", "roads.csv", "text/csv",
                "id,name\n1,Old Road\n".getBytes(StandardCharsets.UTF_8)
        );
        String tableId = tableId(datasetId);
        runUntilTableIsReady(tableId);
        String initialSourceId = fileDatasetTableSourceRepository
                .findByFileDatasetTableIdOrderByCreatedAtAsc(UUID.fromString(tableId))
                .getFirst().getId().toString();

        mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/append",
                        datasetId, tableId
                ).file(filePart(
                        "roads-part-2.csv", "text/csv",
                        "id,name\n2,North Road\n3,East Road\n".getBytes(StandardCharsets.UTF_8)
                )))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.table.currentLoadJobId").isNotEmpty());

        mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/replace-data",
                        datasetId, tableId
                ).file(filePart(
                        "conflicting.csv", "text/csv",
                        "id,name\n9,Conflict\n".getBytes(StandardCharsets.UTF_8)
                )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("逻辑表已经存在正在执行的数据装载"));
        org.junit.jupiter.api.Assertions.assertEquals(
                2, fileObjectStorage.size(), "并发装载提交失败后必须补偿删除新对象"
        );

        runUntilTableIsReady(tableId);
        String appendSourceId = fileDatasetTableSourceRepository
                .findByFileDatasetTableIdOrderBySourceOrderAsc(UUID.fromString(tableId))
                .get(1).getId().toString();
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCount").value(2))
                .andExpect(jsonPath("$.totalRowCount").value(3));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/sources", datasetId, tableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(initialSourceId))
                .andExpect(jsonPath("$[0].sourceOrder").value(0))
                .andExpect(jsonPath("$[1].id").value(appendSourceId))
                .andExpect(jsonPath("$[1].sourceOrder").value(1));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/preview", datasetId, tableId
                ).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[0][1]").value("Old Road"))
                .andExpect(jsonPath("$.rows[1][1]").value("North Road"))
                .andExpect(jsonPath("$.rows[2][1]").value("East Road"));

        mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/sources/{sourceId}/actions/replace",
                        datasetId, tableId, initialSourceId
                ).file(filePart(
                        "roads-current.csv", "text/csv",
                        "id,name\n10,Current Road\n".getBytes(StandardCharsets.UTF_8)
                )))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.table.currentLoadJobId").isNotEmpty());
        runUntilTableIsReady(tableId);
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/sources", datasetId, tableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(initialSourceId))
                .andExpect(jsonPath("$[0].sourceName").value("roads-current"))
                .andExpect(jsonPath("$[0].sourceOrder").value(0))
                .andExpect(jsonPath("$[1].id").value(appendSourceId))
                .andExpect(jsonPath("$[1].sourceOrder").value(1));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCount").value(2))
                .andExpect(jsonPath("$.totalRowCount").value(3));

        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/sources/{sourceId}/actions/delete",
                        datasetId, tableId, appendSourceId
                ))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCount").value(1))
                .andExpect(jsonPath("$.totalRowCount").value(1));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/preview", datasetId, tableId
                ).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0][1]").value("Current Road"));
    }

    @Test
    void keepsCurrentDataAndDeletesTemporaryFileWhenAppendValidationFails() throws Exception {
        String datasetId = createFileDataset(
                "Schema 失败来源", "CSV", "roads.csv", "text/csv",
                "id,name\n1,Old Road\n".getBytes(StandardCharsets.UTF_8)
        );
        String tableId = tableId(datasetId);
        runUntilTableIsReady(tableId);

        String submission = mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/append",
                        datasetId, tableId
                ).file(filePart(
                        "invalid.csv", "text/csv",
                        "id\n2\n".getBytes(StandardCharsets.UTF_8)
                )))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String failedJobId = JsonPath.read(submission, "$.jobId");
        String failedFileId = JsonPath.read(submission, "$.file.id");
        runUntilTableIsReady(tableId);

        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, tableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sourceCount").value(1))
                .andExpect(jsonPath("$.currentLoadJobId").doesNotExist());
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/sources", datasetId, tableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sourceName").value("roads"));
        org.junit.jupiter.api.Assertions.assertEquals(
                FileDatasetParseJobStatus.FAILED,
                fileDatasetParseJobRepository.findById(UUID.fromString(failedJobId)).orElseThrow().getStatus()
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                fileDatasetFileRepository.existsById(UUID.fromString(failedFileId))
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, fileObjectStorage.size());
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/preview", datasetId, tableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0][1]").value("Old Road"));

        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/sources/{sourceId}/actions/retry",
                        datasetId, tableId, UUID.randomUUID()
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAQueuedAppendFileCancelsOnlyTheTemporaryLoad() throws Exception {
        String datasetId = createFileDataset(
                "取消追加", "CSV", "roads.csv", "text/csv",
                "id,name\n1,Old Road\n".getBytes(StandardCharsets.UTF_8)
        );
        String tableId = tableId(datasetId);
        runUntilTableIsReady(tableId);

        String submission = mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/append",
                        datasetId, tableId
                ).file(filePart(
                        "roads-new.csv", "text/csv",
                        "id,name\n2,New Road\n".getBytes(StandardCharsets.UTF_8)
                )))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String appendJobId = JsonPath.read(submission, "$.jobId");
        String appendFileId = JsonPath.read(submission, "$.file.id");

        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/files/{fileId}/actions/delete",
                        datasetId, appendFileId
                ))
                .andExpect(status().isNoContent());

        org.junit.jupiter.api.Assertions.assertTrue(
                fileDatasetTableRepository.existsById(UUID.fromString(tableId))
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                1, fileDatasetTableSourceRepository.countByFileDatasetTableId(UUID.fromString(tableId))
        );
        org.junit.jupiter.api.Assertions.assertNull(
                fileDatasetTableRepository.findById(UUID.fromString(tableId)).orElseThrow().getCurrentLoadJobId()
        );
        org.junit.jupiter.api.Assertions.assertEquals(
                FileDatasetParseJobStatus.CANCELLED,
                fileDatasetParseJobRepository.findById(UUID.fromString(appendJobId)).orElseThrow().getStatus()
        );
        org.junit.jupiter.api.Assertions.assertFalse(
                fileDatasetFileRepository.existsById(UUID.fromString(appendFileId))
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, fileObjectStorage.size());
    }

    @Test
    void locksParsingOptionsDuringInitialLoadAndRemovesManualQueueRoutes() throws Exception {
        var queueEnabled = systemConfigurationRepository.findByConfigKey(
                SystemConfigurationDefinition.FILE_DATASET_PARSING_QUEUE_ENABLED.getConfigKey()
        ).orElseThrow();
        queueEnabled.updateValue("false");
        systemConfigurationRepository.saveAndFlush(queueEnabled);

        String datasetId = createEmptyFileDataset("队列关闭时上传", "CSV");
        String uploaded = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(filesPart(
                                "roads.csv", "text/csv",
                                "id,name\n1,South Road\n".getBytes(StandardCharsets.UTF_8)
                )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tables[0].parseStatus").value("QUEUED"))
                .andExpect(jsonPath("$.tables[0].currentLoadJobId").isNotEmpty())
                .andExpect(jsonPath("$.jobIds.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(uploaded, "$.files[0].id");
        String tableId = JsonPath.read(uploaded, "$.tables[0].id");
        String firstJobId = JsonPath.read(uploaded, "$.jobIds[0]");

        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/parse", datasetId, tableId
                ))
                .andExpect(status().isNotFound());
        org.junit.jupiter.api.Assertions.assertEquals(
                3,
                fileDatasetParseJobRepository.findById(UUID.fromString(firstJobId)).orElseThrow().getMaxAttempts()
        );

        updateParsingOptions(datasetId, """
                {"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"LF",
                "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}
                """)
                .andExpect(status().isConflict());

        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/files/{fileId}/actions/prepare", datasetId, fileId
                ))
                .andExpect(status().isNotFound());

        mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/files/{fileId}/actions/replace", datasetId, fileId
                ).file(filePart(
                        "roads-new.csv", "text/csv", "id\n2\n".getBytes(StandardCharsets.UTF_8)
                )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("单表文件请使用逻辑表的数据来源替换接口"));

        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/files/{fileId}/actions/delete", datasetId, fileId
                ))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(
                FileDatasetParseJobStatus.CANCELLED,
                fileDatasetParseJobRepository.findById(UUID.fromString(firstJobId)).orElseThrow().getStatus()
        );
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.isEmpty());
    }

    @Test
    void rejectsMutatingOrReparsingAFileWhileOneOfItsTablesIsParsing() throws Exception {
        String datasetId = createFileDataset(
                "解析中数据", "CSV", "roads.csv", "text/csv",
                "id,name\n1,South Road\n".getBytes(StandardCharsets.UTF_8)
        );
        FileDatasetParseJobCoordinator.ClaimedJob claimedJob = parseJobCoordinator.claimNext("held-api-test-worker")
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(UUID.fromString(tableId(datasetId)), claimedJob.tableId());

        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/parse",
                        datasetId, tableId(datasetId)
                ))
                .andExpect(status().isNotFound());
        updateParsingOptions(datasetId, """
                {"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"LF",
                "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "文件数据集已经包含文件、表或解析任务，解析参数已经锁定"
                ));
        mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/files/{fileId}/actions/replace",
                        datasetId, fileId(datasetId)
                ).file(filePart(
                        "roads-new.csv", "text/csv", "id\n2\n".getBytes(StandardCharsets.UTF_8)
                )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("单表文件请使用逻辑表的数据来源替换接口"));
        mockMvc.perform(post(
                        "/api/v1/file-datasets/{id}/files/{fileId}/actions/delete",
                        datasetId, fileId(datasetId)
                ))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/delete", datasetId))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(1, fileObjectStorage.size());
    }

    @Test
    void parsesCsvTxtJsonAndJsonLinesAndKeepsTheirSampleMetadata() throws Exception {
        String csvId = createFileDataset("道路 CSV", "CSV", "roads.csv", "text/csv", fixture("roads.csv"));
        parseTableAndGet(csvId, tableId(csvId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(2))
                .andExpect(jsonPath("$.truncated").value(false));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", csvId, tableId(csvId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.fields[1].fieldType").value("LONG"))
                .andExpect(jsonPath("$.fields[2].fieldType").value("BOOLEAN"))
                .andExpect(jsonPath("$.fields[3].fieldType").value("DATE"))
                .andExpect(jsonPath("$.fields[4].fieldType").value("TIMESTAMP_NTZ"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", csvId, tableId(csvId)).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.rows[0][0]").value("South, Road"))
                .andExpect(jsonPath("$.rows[0][5]").value("first line\nsecond line"))
                .andExpect(jsonPath("$.truncated").value(true));

        byte[] textContent = new String(fixture("notes.txt"), StandardCharsets.UTF_8)
                .replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
        String textId = createFileDataset(
                "道路说明", "TXT", "notes.txt", "text/plain", textContent,
                "{\"kind\":\"TEXT\",\"charset\":\"UTF-8\",\"recordDelimiter\":\"CRLF\"}"
        );
        parseTableAndGet(textId, tableId(textId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(3));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", textId, tableId(textId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("value"))
                .andExpect(jsonPath("$.fields[0].fieldType").value("STRING"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", textId, tableId(textId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[1][0]").value("第二行文本"));

        String jsonId = createFileDataset(
                "道路 JSON", "JSON", "roads.json", "application/json", fixture("roads.json"),
                "{\"kind\":\"JSON\",\"charset\":\"UTF-8\",\"rootPointer\":\"/data/items\"}"
        );
        parseTableAndGet(jsonId, tableId(jsonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", jsonId, tableId(jsonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].fieldType").value("LONG"))
                .andExpect(jsonPath("$.fields[2].fieldType").value("BOOLEAN"))
                .andExpect(jsonPath("$.fields[3].fieldType").value("DECIMAL"))
                .andExpect(jsonPath("$.fields[4].fieldType").value("STRING"))
                .andExpect(jsonPath("$.fields[5].fieldType").value("STRING"))
                .andExpect(jsonPath("$.fields[5].nullable").value(true));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", jsonId, tableId(jsonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value(1))
                .andExpect(jsonPath("$.rows[0][4]").value("[\"main\",\"urban\"]"));

        String jsonLinesId = createFileDataset(
                "道路 JSONL", "JSONL", "roads.jsonl", "application/x-ndjson", fixture("roads.jsonl"),
                "{\"kind\":\"JSON_LINES\",\"charset\":\"UTF-8\",\"recordDelimiter\":\"LF\"}"
        );
        parseTableAndGet(jsonLinesId, tableId(jsonLinesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(2));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", jsonLinesId, tableId(jsonLinesId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[3].fieldType").value("DECIMAL"))
                .andExpect(jsonPath("$.fields[4].name").value("district"))
                .andExpect(jsonPath("$.fields[4].nullable").value(true));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", jsonLinesId, tableId(jsonLinesId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][4]").doesNotExist())
                .andExpect(jsonPath("$.rows[1][4]").value("中心城区"));
    }

    @Test
    void parsesXlsXlsxAndParquetWithTheirStoredConfigurations() throws Exception {
        String xlsId = createFileDataset(
                "道路 XLS", "XLS", "roads.xls", "application/vnd.ms-excel", spreadsheetFixture(false)
        );
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables", xlsId).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Roads", "Archive")))
                .andExpect(jsonPath("$.content[*].parseStatus", containsInAnyOrder("QUEUED", "QUEUED")))
                .andExpect(jsonPath("$.content[*].currentLoadJobId").isNotEmpty());
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", xlsId)
                        .file(filesPart("second.xls", "application/vnd.ms-excel", spreadsheetFixture(false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Excel 文件数据集只允许存在一个文件"));
        parseTableAndGet(xlsId, tableId(xlsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(2));
        mockMvc.perform(multipart(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/actions/append",
                        xlsId, tableId(xlsId)
                ).file(filePart(
                        "unsupported.xls", "application/vnd.ms-excel", spreadsheetFixture(false)
                )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Excel/GDB 暂不支持表级追加或覆盖，请使用整文件替换"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", xlsId, tableId(xlsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.fields[1].name").value("name_2"))
                .andExpect(jsonPath("$.fields[2].name").value("column_3"))
                .andExpect(jsonPath("$.fields[3].fieldType").value("DATE"))
                .andExpect(jsonPath("$.fields[4].fieldType").value("TIMESTAMP_NTZ"))
                .andExpect(jsonPath("$.fields[5].fieldType").value("BOOLEAN"))
                .andExpect(jsonPath("$.fields[6].fieldType").value("DECIMAL"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", xlsId, tableId(xlsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("name"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", xlsId, tableId(xlsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value("South Road"))
                .andExpect(jsonPath("$.rows[0][1]").value(7))
                .andExpect(jsonPath("$.rows[1][2]").value("secondary"));
        String xlsArchiveTableId = sourceTableId(xlsId, "Archive");
        parseTableAndGet(xlsId, xlsArchiveTableId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampledRecordCount").value(1));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/sources",
                        xlsId, xlsArchiveTableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceKey").value("Archive"));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/schema",
                        xlsId, xlsArchiveTableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("archive_id"));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/preview",
                        xlsId, xlsArchiveTableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value("A-1"));

        String xlsxId = createFileDataset(
                "道路 XLSX", "XLSX", "roads.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", spreadsheetFixture(true)
        );
        parseTableAndGet(xlsxId, tableId(xlsxId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", xlsxId, tableId(xlsxId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.fields[1].name").value("name_2"))
                .andExpect(jsonPath("$.fields[3].fieldType").value("DATE"))
                .andExpect(jsonPath("$.fields[4].fieldType").value("TIMESTAMP_NTZ"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", xlsxId, tableId(xlsxId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value("South Road"))
                .andExpect(jsonPath("$.rows[0][7]").value(8));
        String xlsxArchiveTableId = sourceTableId(xlsxId, "Archive");
        parseTableAndGet(xlsxId, xlsxArchiveTableId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampledRecordCount").value(1));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/sources",
                        xlsxId, xlsxArchiveTableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceKey").value("Archive"));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/schema",
                        xlsxId, xlsxArchiveTableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(1));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/preview",
                        xlsxId, xlsxArchiveTableId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value("A-1"));

        String parquetId = createFileDataset(
                "道路 Parquet", "PARQUET", "roads.parquet", "application/vnd.apache.parquet", parquetFixture()
        );
        parseTableAndGet(parquetId, tableId(parquetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", parquetId, tableId(parquetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.fields[1].fieldType").value("LONG"))
                .andExpect(jsonPath("$.fields[2].fieldType").value("DATE"))
                .andExpect(jsonPath("$.fields[3].fieldType").value("TIMESTAMP"))
                .andExpect(jsonPath("$.fields[4].fieldType").value("DECIMAL"))
                .andExpect(jsonPath("$.fields[5].fieldType").value("STRING"))
                .andExpect(jsonPath("$.fields[6].fieldType").value("STRING"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/preview", parquetId, tableId(parquetId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value("South Road"))
                .andExpect(jsonPath("$.rows[0][5]").value("[\"main\",\"urban\"]"))
                .andExpect(jsonPath("$.rows[0][6]").value("{\"district\":\"中心城区\"}"));
    }

    @Test
    void parsesTsvAndGzipTextFilesAndAbortsIncompleteObjectSamples() throws Exception {
        String tsvId = createFileDataset(
                "道路 TSV", "TSV", "roads.tsv", "text/tab-separated-values",
                "name\troad_id\tactive\nSouth Road\t7\ttrue\nNorth Road\t8\tfalse\n".getBytes(StandardCharsets.UTF_8)
        );
        parseTableAndGet(tsvId, tableId(tsvId))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/file-datasets/{id}/tables/{tableId}/schema", tsvId, tableId(tsvId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[1].fieldType").value("LONG"));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/update", tsvId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"道路 TSV","parsingOptions":{"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"AUTO",
                                "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("TSV 文件的字段分隔符必须是制表符"));

        StringBuilder csv = new StringBuilder("road_id,name\n");
        for (int index = 1; index <= 1_001; index++) {
            csv.append(index).append(",Road ").append(index).append('\n');
        }
        byte[] compressedContent = gzip(csv.toString().getBytes(StandardCharsets.UTF_8));
        String gzipId = createFileDataset("道路压缩 CSV", "CSV", "roads.csv.gz", "application/gzip", compressedContent);
        parseTableAndGet(gzipId, tableId(gzipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sampledRecordCount").value(1_000))
                .andExpect(jsonPath("$.truncated").value(true));
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.abortCount() > 0);
        downloadContent(gzipId).andExpect(status().isOk()).andExpect(content().bytes(compressedContent));

        String invalidGzipId = createEmptyFileDataset("错误压缩", "CSV");
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", invalidGzipId)
                        .file(filesPart("roads.csv.gz", "application/gzip", "not-gzip".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("文件扩展名为 GZIP，但内容不是有效 GZIP"));
        String invalidExtensionId = createEmptyFileDataset("错误压缩扩展名", "CSV");
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", invalidExtensionId)
                        .file(filesPart("roads.csv", "application/gzip", compressedContent)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("检测到 GZIP 内容，请将文件名补全为 .gz"));
    }

    @Test
    void parsesAvroObjectContainerFilesAcrossSupportedCodecs() throws Exception {
        for (String codec : List.of("null", "deflate", "snappy", "bzip2", "xz", "zstandard")) {
            String datasetId = createFileDataset(
                    "道路 Avro " + codec, "AVRO", "roads-" + codec + ".avro", "application/avro",
                    avroFixture(codec, true)
            );
            parseTableAndGet(datasetId, tableId(datasetId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.parseStatus").value("READY"));
            mockMvc.perform(get(
                            "/api/v1/file-datasets/{id}/tables/{tableId}/schema",
                            datasetId, tableId(datasetId)
                    ))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fields[0].name").value("name"))
                    .andExpect(jsonPath("$.fields[1].fieldType").value("LONG"))
                    .andExpect(jsonPath("$.fields[2].fieldType").value("DATE"))
                    .andExpect(jsonPath("$.fields[3].fieldType").value("TIMESTAMP"))
                    .andExpect(jsonPath("$.fields[4].fieldType").value("DECIMAL"))
                    .andExpect(jsonPath("$.fields[5].fieldType").value("STRING"))
                    .andExpect(jsonPath("$.fields[6].fieldType").value("STRING"))
                    .andExpect(jsonPath("$.fields[7].nullable").value(true))
                    .andExpect(jsonPath("$.fields[9].fieldType").value("STRING"));
            mockMvc.perform(get(
                            "/api/v1/file-datasets/{id}/tables/{tableId}/preview",
                            datasetId, tableId(datasetId)
                    ))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[0][0]").value("South Road"))
                    .andExpect(jsonPath("$.rows[0][4]").value(123.45))
                    .andExpect(jsonPath("$.rows[0][5]").value("[\"main\",\"urban\"]"))
                    .andExpect(jsonPath("$.rows[0][6]").value("{\"district\":\"中心城区\"}"))
                    .andExpect(jsonPath("$.rows[0][8]").value("AQID"))
                    .andExpect(jsonPath("$.rows[0][9]").value("9"));
        }

        String emptyDatasetId = createFileDataset(
                "空 Avro", "AVRO", "empty.avro", "application/avro", avroFixture("null", false)
        );
        parseTableAndGet(emptyDatasetId, tableId(emptyDatasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(0));
        mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/tables/{tableId}/schema",
                        emptyDatasetId, tableId(emptyDatasetId)
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields.length()").value(10));

        String invalidAvroId = createEmptyFileDataset("错误 Avro", "AVRO");
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", invalidAvroId)
                        .file(filesPart("invalid.avro", "application/avro", "not-an-avro-file".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("AVRO 文件内容不是有效的 Object Container File"));
    }

    private byte[] gzip(byte[] content) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(content);
            gzipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private byte[] avroFixture(String codec, boolean writeRecord) throws IOException {
        Schema schema = new Schema.Parser().parse("""
                {
                  "type":"record", "name":"Road", "namespace":"cn.superhuang.data.scalpel.fixture",
                  "fields":[
                    {"name":"name","type":"string"},
                    {"name":"road_id","type":"long"},
                    {"name":"opened","type":{"type":"int","logicalType":"date"}},
                    {"name":"observed_at","type":{"type":"long","logicalType":"timestamp-micros"}},
                    {"name":"amount","type":{"type":"bytes","logicalType":"decimal","precision":10,"scale":2}},
                    {"name":"tags","type":{"type":"array","items":"string"}},
                    {"name":"attributes","type":{"type":"map","values":"string"}},
                    {"name":"district","type":["null","string"],"default":null},
                    {"name":"payload","type":"bytes"},
                    {"name":"mixed","type":["int","string"]}
                  ]
                }
                """);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.setCodec(CodecFactory.fromString(codec));
            writer.create(schema, outputStream);
            if (writeRecord) {
                GenericRecord record = new GenericData.Record(schema);
                record.put("name", "South Road");
                record.put("road_id", 7L);
                record.put("opened", (int) LocalDate.of(2024, 1, 2).toEpochDay());
                record.put("observed_at", 1_704_190_200_000_000L);
                Schema amountSchema = schema.getField("amount").schema();
                record.put("amount", new Conversions.DecimalConversion().toBytes(
                        new BigDecimal("123.45"), amountSchema, amountSchema.getLogicalType()
                ));
                record.put("tags", List.of("main", "urban"));
                record.put("attributes", Map.of("district", "中心城区"));
                record.put("district", null);
                record.put("payload", ByteBuffer.wrap(new byte[]{1, 2, 3}));
                record.put("mixed", 9);
                writer.append(record);
            }
            writer.flush();
            return outputStream.toByteArray();
        }
    }

    private byte[] spreadsheetFixture(boolean xlsx) throws IOException {
        try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet roads = workbook.createSheet("Roads");
            roads.createRow(0).createCell(0).setCellValue("道路数据导入说明");
            Row header = roads.createRow(1);
            String[] headers = {"name", "name", "", "opened", "updated", "active", "amount", "score"};
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));

            Row south = roads.createRow(2);
            south.createCell(0).setCellValue("South Road");
            south.createCell(1).setCellValue(7);
            south.createCell(3).setCellValue(LocalDate.of(2024, 1, 2));
            south.getCell(3).setCellStyle(dateStyle);
            south.createCell(4).setCellValue(LocalDateTime.of(2024, 1, 2, 10, 30));
            south.getCell(4).setCellStyle(dateTimeStyle);
            south.createCell(5).setCellValue(true);
            south.createCell(6).setCellValue(12.5D);
            south.createCell(7).setCellFormula("B3+1");

            Row north = roads.createRow(3);
            north.createCell(0).setCellValue("North Road");
            north.createCell(1).setCellValue(8);
            north.createCell(2).setCellValue("secondary");
            north.createCell(3).setCellValue(LocalDate.of(2024, 1, 3));
            north.getCell(3).setCellStyle(dateStyle);
            north.createCell(4).setCellValue(LocalDateTime.of(2024, 1, 3, 11, 45));
            north.getCell(4).setCellStyle(dateTimeStyle);
            north.createCell(5).setCellValue(false);
            north.createCell(6).setCellValue(18.25D);
            north.createCell(7).setCellFormula("B4+1");
            Sheet archive = workbook.createSheet("Archive");
            archive.createRow(0).createCell(0).setCellValue("归档数据导入说明");
            archive.createRow(1).createCell(0).setCellValue("archive_id");
            archive.createRow(2).createCell(0).setCellValue("A-1");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] parquetFixture() throws IOException {
        MessageType schema = MessageTypeParser.parseMessageType("""
                message roads {
                  required binary name (STRING);
                  optional int32 road_id;
                  optional int32 opened (DATE);
                  optional int64 observed_at (TIMESTAMP(MILLIS,true));
                  optional int64 amount (DECIMAL(10,2));
                  optional group tags (LIST) {
                    repeated group list {
                      optional binary element (STRING);
                    }
                  }
                  optional group attributes (MAP) {
                    repeated group key_value {
                      required binary key (STRING);
                      optional binary value (STRING);
                    }
                  }
                }
        """);
        Path file = Files.createTempFile("data-scalpel-parquet-fixture-", ".parquet");
        try {
            Files.deleteIfExists(file);
            SimpleGroupFactory groups = new SimpleGroupFactory(schema);
            try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new LocalOutputFile(file)).withType(schema).build()) {
                Group south = groups.newGroup()
                        .append("name", "South Road")
                        .append("road_id", 7)
                        .append("opened", (int) LocalDate.of(2024, 1, 2).toEpochDay())
                        .append("observed_at", 1_704_190_200_000L)
                        .append("amount", 1250L);
                south.addGroup("tags").addGroup("list").append("element", "main");
                south.getGroup("tags", 0).addGroup("list").append("element", "urban");
                south.addGroup("attributes").addGroup("key_value")
                        .append("key", "district").append("value", "中心城区");
                writer.write(south);
                writer.write(groups.newGroup().append("name", "North Road").append("road_id", 8));
            }
            return Files.readAllBytes(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private String createFileDataset(String name, String format, String fileName, String contentType, byte[] content) throws Exception {
        return createFileDataset(name, format, fileName, contentType, content, defaultOptions(format));
    }

    private String createFileDataset(
            String name,
            String format,
            String fileName,
            String contentType,
            byte[] content,
            String parsingOptions
    ) throws Exception {
        String datasetId = createEmptyFileDataset(name, format, parsingOptions);
        String uploaded = mockMvc.perform(multipart("/api/v1/file-datasets/{id}/files", datasetId)
                        .file(filesPart(fileName, contentType, content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        fileIds.put(datasetId, JsonPath.read(uploaded, "$.files[0].id"));
        tableIds.put(datasetId, JsonPath.read(uploaded, "$.tables[0].id"));
        return datasetId;
    }

    private String createEmptyFileDataset(String name, String format) throws Exception {
        return createEmptyFileDataset(name, format, defaultOptions(format));
    }

    private String createEmptyFileDataset(String name, String format, String parsingOptions) throws Exception {
        String type = switch (format) {
            case "XLS", "XLSX" -> "EXCEL";
            default -> format;
        };
        String created = mockMvc.perform(post("/api/v1/file-datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","type":"%s","parsingOptions":%s}
                                """.formatted(name, type, parsingOptions)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private String defaultOptions(String format) {
        return switch (format) {
            case "CSV" -> """
                    {"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"AUTO",
                    "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}
                    """;
            case "TSV" -> """
                    {"kind":"CSV","charset":"UTF-8","fieldDelimiter":"\\t","recordDelimiter":"AUTO",
                    "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}
                    """;
            case "TXT" -> "{\"kind\":\"TEXT\",\"charset\":\"UTF-8\",\"recordDelimiter\":\"AUTO\"}";
            case "JSON" -> "{\"kind\":\"JSON\",\"charset\":\"UTF-8\",\"rootPointer\":null}";
            case "JSONL" -> "{\"kind\":\"JSON_LINES\",\"charset\":\"UTF-8\",\"recordDelimiter\":\"AUTO\"}";
            case "XLS", "XLSX" -> "{\"kind\":\"SPREADSHEET\",\"headerRowIndex\":1,\"dataStartRowIndex\":2}";
            case "PARQUET" -> "{\"kind\":\"PARQUET\"}";
            case "AVRO" -> "{\"kind\":\"AVRO\"}";
            default -> throw new IllegalArgumentException("未知测试格式：" + format);
        };
    }

    private ResultActions downloadContent(String datasetId) throws Exception {
        MvcResult result = mockMvc.perform(get(
                        "/api/v1/file-datasets/{id}/files/{fileId}/content", datasetId, fileId(datasetId)
                ))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult();
        return mockMvc.perform(asyncDispatch(result));
    }

    private ResultActions updateParsingOptions(String datasetId, String parsingOptions) throws Exception {
        return mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/update", datasetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"测试数据集","parsingOptions":%s}
                        """.formatted(parsingOptions)));
    }

    private String tableId(String datasetId) {
        return tableIds.get(datasetId);
    }

    private String fileId(String datasetId) {
        return fileIds.get(datasetId);
    }

    private String sourceTableId(String datasetId, String sourceName) {
        return fileDatasetTableRepository.findByFileDatasetIdOrderByCreatedAtAsc(UUID.fromString(datasetId)).stream()
                .filter(table -> sourceName.equals(table.getName()))
                .findFirst()
                .map(table -> table.getId().toString())
                .orElseThrow();
    }

    private ResultActions parseTableAndGet(String datasetId, String tableId) throws Exception {
        runUntilTableIsReady(tableId);
        return mockMvc.perform(get(
                "/api/v1/file-datasets/{id}/tables/{tableId}", datasetId, tableId
        ));
    }

    private void runUntilTableIsReady(String tableId) {
        UUID id = UUID.fromString(tableId);
        for (int attempt = 0; attempt < 100; attempt++) {
            FileDatasetTable table = fileDatasetTableRepository.findById(id).orElseThrow();
            if ((table.getParseStatus()
                    == cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus.READY
                    || table.getParseStatus()
                    == cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus.SCHEMA_READY)
                    && table.getCurrentLoadJobId() == null) {
                return;
            }
            FileDatasetParseWorker.ExecutionOutcome outcome = parseWorker.runOne("file-dataset-api-test-" + UUID.randomUUID());
            if (outcome == FileDatasetParseWorker.ExecutionOutcome.NO_JOB) {
                throw new AssertionError(
                        "没有可执行任务，目标表状态为 " + table.getParseStatus()
                                + "，当前任务为 " + table.getCurrentLoadJobId()
                );
            }
        }
        throw new AssertionError("目标表未在限定任务数内解析完成：" + tableId);
    }

    private byte[] fixture(String name) throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream("/filedataset/" + name)) {
            if (inputStream == null) {
                throw new IllegalStateException("缺少测试样本：" + name);
            }
            return inputStream.readAllBytes();
        }
    }

    private String createDirectory(String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/directories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"FILE_DATASET\",\"name\":\"%s\",\"sortOrder\":10}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private MockMultipartFile filePart(String fileName, String contentType, byte[] content) {
        return new MockMultipartFile("file", fileName, contentType, content);
    }

    private MockMultipartFile filesPart(String fileName, String contentType, byte[] content) {
        return new MockMultipartFile("files", fileName, contentType, content);
    }

    private void clearData() {
        systemConfigurationRepository.findByConfigKey(
                SystemConfigurationDefinition.FILE_DATASET_PARSING_QUEUE_ENABLED.getConfigKey()
        ).ifPresent(configuration -> {
            configuration.updateValue("true");
            systemConfigurationRepository.saveAndFlush(configuration);
        });
        fileDatasetFieldRepository.deleteAll();
        fileDatasetParseJobRepository.deleteAll();
        fileDatasetTableSourceRepository.deleteAll();
        fileDatasetTableRepository.deleteAll();
        fileDatasetFileRepository.deleteAll();
        fileDatasetRepository.deleteAll();
        directoryRepository.deleteAll();
        fileObjectStorage.clear();
        fileIds.clear();
        tableIds.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StorageConfiguration {

        @Bean
        InMemoryFileObjectStorage fileObjectStorage() {
            return new InMemoryFileObjectStorage();
        }
    }

    static class InMemoryFileObjectStorage implements FileObjectStorage {

        private final Map<String, StoredValue> values = new ConcurrentHashMap<>();
        private final AtomicInteger abortCount = new AtomicInteger();
        private final AtomicBoolean failNextStore = new AtomicBoolean();
        private final AtomicBoolean failNextDelete = new AtomicBoolean();
        private final AtomicReference<Runnable> afterNextStore = new AtomicReference<>();

        @Override
        public StoredFileObject store(String objectKey, InputStream inputStream, long contentLength, String contentType) {
            assertFalse(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "对象存储上传不得处于管理数据库事务中"
            );
            if (failNextStore.compareAndSet(true, false)) {
                throw new FileStorageException("模拟对象存储上传失败", null);
            }
            try {
                values.put(objectKey, new StoredValue(inputStream.readAllBytes(), contentType));
                Runnable callback = afterNextStore.getAndSet(null);
                if (callback != null) {
                    callback.run();
                }
                return new StoredFileObject("in-memory-" + objectKey);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public FileObjectContent open(String objectKey) {
            assertFalse(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "对象存储读取不得处于管理数据库事务中"
            );
            StoredValue value = values.get(objectKey);
            if (value == null) {
                throw new FileStorageObjectNotFoundException("对象不存在", null);
            }
            return new FileObjectContent(
                    new ByteArrayInputStream(value.content()), value.content().length, value.contentType(), abortCount::incrementAndGet
            );
        }

        @Override
        public void delete(String objectKey) {
            if (failNextDelete.compareAndSet(true, false)) {
                throw new FileStorageException("模拟对象存储删除失败", null);
            }
            values.remove(objectKey);
        }

        void failNextStore() {
            failNextStore.set(true);
        }

        void failNextDelete() {
            failNextDelete.set(true);
        }

        void afterNextStore(Runnable callback) {
            afterNextStore.set(callback);
        }

        int size() {
            return values.size();
        }

        boolean isEmpty() {
            return values.isEmpty();
        }

        int abortCount() {
            return abortCount.get();
        }

        void clear() {
            values.clear();
            abortCount.set(0);
            failNextStore.set(false);
            failNextDelete.set(false);
            afterNextStore.set(null);
        }

        private record StoredValue(byte[] content, String contentType) {
        }
    }
}
