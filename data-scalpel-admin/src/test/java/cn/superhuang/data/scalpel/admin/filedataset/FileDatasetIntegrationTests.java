package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.directory.repository.DirectoryRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileObjectStorage;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageObjectNotFoundException;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static cn.superhuang.data.scalpel.admin.support.AuthenticationTestSupport.loginAsAdministrator;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.containsString;
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
    private DirectoryRepository directoryRepository;

    @Autowired
    private InMemoryFileObjectStorage fileObjectStorage;

    private MockMvc mockMvc;

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
    void managesStoredFilesWithoutParsingAndProtectsTheirDirectories() throws Exception {
        String directoryId = createDirectory("文件资料");
        byte[] firstContent = "id,name\n1,Old Road\n".getBytes(StandardCharsets.UTF_8);
        String created = mockMvc.perform(multipart("/api/v1/file-datasets")
                        .file(jsonPart("""
                                {"name":"道路数据","directoryId":"%s","format":"CSV","description":"原始道路数据"}
                                """.formatted(directoryId)))
                        .file(filePart("roads.csv", "text/csv", firstContent)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("道路数据"))
                .andExpect(jsonPath("$.directoryId").value(directoryId))
                .andExpect(jsonPath("$.format").value("CSV"))
                .andExpect(jsonPath("$.originalFileName").value("roads.csv"))
                .andExpect(jsonPath("$.sizeBytes").value(firstContent.length))
                .andExpect(jsonPath("$.parseStatus").value("UNPARSED"))
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String datasetId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/v1/file-datasets")
                        .param("search", "name:*\"道路\"* AND format:\"CSV\"")
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
                                {"name":"道路数据（更新）","directoryId":"%s","format":"CSV","description":"更新说明"}
                                """.formatted(directoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("道路数据（更新）"))
                .andExpect(jsonPath("$.description").value("更新说明"));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/configure-parsing", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "options": {
                                    "kind": "CSV",
                                    "charset": "UTF-8",
                                    "fieldDelimiter": ",",
                                    "recordDelimiter": "AUTO",
                                    "quoteCharacter": "\\\"",
                                    "escapeCharacter": "\\\\",
                                    "firstRowHeader": true
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.parseStatus").value("UNPARSED"))
                .andExpect(jsonPath("$.options.kind").value("CSV"))
                .andExpect(jsonPath("$.options.charset").value("UTF-8"))
                .andExpect(jsonPath("$.options.firstRowHeader").value(true));

        mockMvc.perform(get("/api/v1/file-datasets/{id}/parsing", datasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.options.fieldDelimiter").value(","));

        byte[] replacementContent = "PAR1replacement".getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/v1/file-datasets/{id}/actions/replace-content", datasetId)
                        .file(jsonPart("{" + "\"format\":\"PARQUET\"}"))
                        .file(filePart("roads.parquet", "application/octet-stream", replacementContent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("PARQUET"))
                .andExpect(jsonPath("$.originalFileName").value("roads.parquet"))
                .andExpect(jsonPath("$.parseStatus").value("UNPARSED"))
                .andExpect(jsonPath("$.parsingConfigured").value(false));

        downloadContent(datasetId)
                .andExpect(status().isOk())
                .andExpect(content().bytes(replacementContent));
        org.junit.jupiter.api.Assertions.assertEquals(1, fileObjectStorage.size());

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
        mockMvc.perform(multipart("/api/v1/file-datasets")
                        .file(jsonPart("{\"name\":\"错误格式\",\"format\":\"PARQUET\"}"))
                        .file(filePart("roads.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("PARQUET 格式的文件扩展名不匹配"));
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.isEmpty());

        String created = mockMvc.perform(multipart("/api/v1/file-datasets")
                        .file(jsonPart("{\"name\":\"文本数据\",\"format\":\"CSV\"}"))
                        .file(filePart("roads.csv", "text/csv", "id\n1\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String datasetId = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/update", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"文本数据\",\"format\":\"PARQUET\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("PARQUET 格式的文件扩展名不匹配"));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/configure-parsing", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"options":{"kind":"PARQUET"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("解析参数类型与文件格式不匹配"));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/configure-parsing", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "options": {
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
    void parsesCsvTxtJsonAndJsonLinesAndKeepsTheirSampleMetadata() throws Exception {
        String csvId = createFileDataset("道路 CSV", "CSV", "roads.csv", "text/csv", fixture("roads.csv"));
        configureParsing(csvId, """
                {
                  "options": {
                    "kind": "CSV", "charset": "UTF-8", "fieldDelimiter": ",",
                    "recordDelimiter": "AUTO", "quoteCharacter": "\\\"",
                    "escapeCharacter": "\\\\", "firstRowHeader": true
                  }
                }
                """);
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", csvId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(2))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.fields[1].logicalType").value("INTEGER"))
                .andExpect(jsonPath("$.fields[2].logicalType").value("BOOLEAN"))
                .andExpect(jsonPath("$.fields[3].logicalType").value("DATE"))
                .andExpect(jsonPath("$.fields[4].logicalType").value("DATETIME"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/preview", csvId).param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.rows[0][0]").value("South, Road"))
                .andExpect(jsonPath("$.rows[0][5]").value("first line\nsecond line"))
                .andExpect(jsonPath("$.truncated").value(true));

        byte[] textContent = new String(fixture("notes.txt"), StandardCharsets.UTF_8)
                .replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
        String textId = createFileDataset("道路说明", "TXT", "notes.txt", "text/plain", textContent);
        configureParsing(textId, """
                {"options":{"kind":"TEXT","charset":"UTF-8","recordDelimiter":"CRLF"}}
                """);
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", textId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(3))
                .andExpect(jsonPath("$.fields[0].name").value("value"))
                .andExpect(jsonPath("$.fields[0].logicalType").value("STRING"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/preview", textId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[1][0]").value("第二行文本"));

        String jsonId = createFileDataset("道路 JSON", "JSON", "roads.json", "application/json", fixture("roads.json"));
        configureParsing(jsonId, """
                {"options":{"kind":"JSON","charset":"UTF-8","rootPointer":"/data/items"}}
                """);
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", jsonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.fields[0].logicalType").value("INTEGER"))
                .andExpect(jsonPath("$.fields[2].logicalType").value("BOOLEAN"))
                .andExpect(jsonPath("$.fields[3].logicalType").value("DECIMAL"))
                .andExpect(jsonPath("$.fields[4].logicalType").value("ARRAY"))
                .andExpect(jsonPath("$.fields[5].logicalType").value("JSON"))
                .andExpect(jsonPath("$.fields[5].nullable").value(true));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/preview", jsonId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value(1))
                .andExpect(jsonPath("$.rows[0][4]").value("[\"main\",\"urban\"]"));

        String jsonLinesId = createFileDataset(
                "道路 JSONL", "JSONL", "roads.jsonl", "application/x-ndjson", fixture("roads.jsonl")
        );
        configureParsing(jsonLinesId, """
                {"options":{"kind":"JSON_LINES","charset":"UTF-8","recordDelimiter":"LF"}}
                """);
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", jsonLinesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(2))
                .andExpect(jsonPath("$.fields[3].logicalType").value("DECIMAL"))
                .andExpect(jsonPath("$.fields[4].name").value("district"))
                .andExpect(jsonPath("$.fields[4].nullable").value(true));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/preview", jsonLinesId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][4]").doesNotExist())
                .andExpect(jsonPath("$.rows[1][4]").value("中心城区"));
    }

    @Test
    void parsesXlsXlsxAndParquetWithTheirStoredConfigurations() throws Exception {
        String xlsId = createFileDataset(
                "道路 XLS", "XLS", "roads.xls", "application/vnd.ms-excel", spreadsheetFixture(false)
        );
        configureParsing(xlsId, """
                {"options":{"kind":"SPREADSHEET","sheetName":"Roads","headerRowIndex":1,"dataStartRowIndex":2}}
                """);
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", xlsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(2))
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.fields[1].name").value("name_2"))
                .andExpect(jsonPath("$.fields[2].name").value("column_3"))
                .andExpect(jsonPath("$.fields[3].logicalType").value("DATE"))
                .andExpect(jsonPath("$.fields[4].logicalType").value("DATETIME"))
                .andExpect(jsonPath("$.fields[5].logicalType").value("BOOLEAN"))
                .andExpect(jsonPath("$.fields[6].logicalType").value("DECIMAL"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/preview", xlsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value("South Road"))
                .andExpect(jsonPath("$.rows[0][1]").value(7))
                .andExpect(jsonPath("$.rows[1][2]").value("secondary"));

        String xlsxId = createFileDataset(
                "道路 XLSX", "XLSX", "roads.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", spreadsheetFixture(true)
        );
        configureParsing(xlsxId, """
                {"options":{"kind":"SPREADSHEET","sheetName":"Roads","headerRowIndex":1,"dataStartRowIndex":2}}
                """);
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", xlsxId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.fields[1].name").value("name_2"))
                .andExpect(jsonPath("$.fields[3].logicalType").value("DATE"))
                .andExpect(jsonPath("$.fields[4].logicalType").value("DATETIME"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/preview", xlsxId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0][0]").value("South Road"))
                .andExpect(jsonPath("$.rows[0][7]").value(8));

        String parquetId = createFileDataset(
                "道路 Parquet", "PARQUET", "roads.parquet", "application/vnd.apache.parquet", parquetFixture()
        );
        configureParsing(parquetId, "{\"options\":{\"kind\":\"PARQUET\"}}");
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", parquetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.fields[0].name").value("name"))
                .andExpect(jsonPath("$.fields[1].logicalType").value("INTEGER"))
                .andExpect(jsonPath("$.fields[2].logicalType").value("DATE"))
                .andExpect(jsonPath("$.fields[3].logicalType").value("DATETIME"))
                .andExpect(jsonPath("$.fields[4].logicalType").value("DECIMAL"))
                .andExpect(jsonPath("$.fields[5].logicalType").value("ARRAY"))
                .andExpect(jsonPath("$.fields[6].logicalType").value("JSON"));
        mockMvc.perform(get("/api/v1/file-datasets/{id}/preview", parquetId))
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
        configureParsing(tsvId, """
                {"options":{"kind":"CSV","charset":"UTF-8","fieldDelimiter":"\\t","recordDelimiter":"AUTO",
                "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}}
                """);
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", tsvId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("TSV"))
                .andExpect(jsonPath("$.compression").value("NONE"))
                .andExpect(jsonPath("$.fields[1].logicalType").value("INTEGER"));

        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/configure-parsing", tsvId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"options":{"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"AUTO",
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
        configureParsing(gzipId, """
                {"options":{"kind":"CSV","charset":"UTF-8","fieldDelimiter":",","recordDelimiter":"AUTO",
                "quoteCharacter":"\\\"","escapeCharacter":"\\\\","firstRowHeader":true}}
                """);
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", gzipId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("CSV"))
                .andExpect(jsonPath("$.compression").value("GZIP"))
                .andExpect(jsonPath("$.sampledRecordCount").value(1_000))
                .andExpect(jsonPath("$.truncated").value(true));
        org.junit.jupiter.api.Assertions.assertTrue(fileObjectStorage.abortCount() > 0);
        mockMvc.perform(get("/api/v1/file-datasets/{id}/content", gzipId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(compressedContent));

        mockMvc.perform(multipart("/api/v1/file-datasets")
                        .file(jsonPart("{\"name\":\"错误压缩\",\"format\":\"CSV\"}"))
                        .file(filePart("roads.csv.gz", "application/gzip", "not-gzip".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("文件扩展名为 GZIP，但内容不是有效 GZIP"));
        mockMvc.perform(multipart("/api/v1/file-datasets")
                        .file(jsonPart("{\"name\":\"错误压缩扩展名\",\"format\":\"CSV\"}"))
                        .file(filePart("roads.csv", "application/gzip", compressedContent)))
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
            configureParsing(datasetId, "{\"options\":{\"kind\":\"AVRO\"}}");
            mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", datasetId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.format").value("AVRO"))
                    .andExpect(jsonPath("$.compression").value("NONE"))
                    .andExpect(jsonPath("$.parseStatus").value("READY"))
                    .andExpect(jsonPath("$.fields[0].name").value("name"))
                    .andExpect(jsonPath("$.fields[1].logicalType").value("INTEGER"))
                    .andExpect(jsonPath("$.fields[2].logicalType").value("DATE"))
                    .andExpect(jsonPath("$.fields[3].logicalType").value("DATETIME"))
                    .andExpect(jsonPath("$.fields[4].logicalType").value("DECIMAL"))
                    .andExpect(jsonPath("$.fields[5].logicalType").value("ARRAY"))
                    .andExpect(jsonPath("$.fields[6].logicalType").value("JSON"))
                    .andExpect(jsonPath("$.fields[7].nullable").value(true))
                    .andExpect(jsonPath("$.fields[9].logicalType").value("JSON"));
            mockMvc.perform(get("/api/v1/file-datasets/{id}/preview", datasetId))
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
        configureParsing(emptyDatasetId, "{\"options\":{\"kind\":\"AVRO\"}}");
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/parse", emptyDatasetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("READY"))
                .andExpect(jsonPath("$.sampledRecordCount").value(0))
                .andExpect(jsonPath("$.fields.length()").value(10));

        mockMvc.perform(multipart("/api/v1/file-datasets")
                        .file(jsonPart("{\"name\":\"错误 Avro\",\"format\":\"AVRO\"}"))
                        .file(filePart("invalid.avro", "application/avro", "not-an-avro-file".getBytes(StandardCharsets.UTF_8))))
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
            workbook.createSheet("Archive").createRow(0).createCell(0).setCellValue("ignored");
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
        String created = mockMvc.perform(multipart("/api/v1/file-datasets")
                        .file(jsonPart("{\"name\":\"%s\",\"format\":\"%s\"}".formatted(name, format)))
                        .file(filePart(fileName, contentType, content)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(created, "$.id");
    }

    private ResultActions downloadContent(String datasetId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/file-datasets/{id}/content", datasetId))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(result));
    }

    private void configureParsing(String datasetId, String requestBody) throws Exception {
        mockMvc.perform(post("/api/v1/file-datasets/{id}/actions/configure-parsing", datasetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parseStatus").value("UNPARSED"));
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

    private MockMultipartFile jsonPart(String content) {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, content.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile filePart(String fileName, String contentType, byte[] content) {
        return new MockMultipartFile("file", fileName, contentType, content);
    }

    private void clearData() {
        fileDatasetRepository.deleteAll();
        directoryRepository.deleteAll();
        fileObjectStorage.clear();
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

        @Override
        public StoredFileObject store(String objectKey, InputStream inputStream, long contentLength, String contentType) {
            assertFalse(
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    "对象存储上传不得处于管理数据库事务中"
            );
            try {
                values.put(objectKey, new StoredValue(inputStream.readAllBytes(), contentType));
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
            values.remove(objectKey);
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
        }

        private record StoredValue(byte[] content, String contentType) {
        }
    }
}
