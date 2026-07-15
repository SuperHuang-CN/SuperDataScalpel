package cn.superhuang.data.scalpel.admin.filedataset;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.AvroFileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.CsvFileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParseSource;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingConfiguration;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.JsonFileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.JsonLinesFileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.ParquetFileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.TextFileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.XlsFileDatasetParser;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.XlsxFileDatasetParser;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Generates retained upload samples only when explicitly requested from Maven. */
@EnabledIfSystemProperty(named = "generateManualFileDatasetSamples", matches = "true")
class ManualFileDatasetSampleDataGeneratorTest {

    private static final Path OUTPUT_DIRECTORY = Path.of("..", "manual-test-data", "file-dataset")
            .toAbsolutePath().normalize();

    @Test
    void writesManualUploadSamples() throws IOException {
        Files.createDirectories(OUTPUT_DIRECTORY);
        byte[] csv = """
                road_id,name,opened,active,amount
                7,南环路,2024-01-02,true,123.45
                8,北环路,2024-01-03,false,67.89
                9,东环路,2024-01-04,true,88.00
                """.getBytes(StandardCharsets.UTF_8);
        byte[] tsv = """
                name\troad_id\topened\tactive
                南环路\t7\t2024-01-02\ttrue
                北环路\t8\t2024-01-03\tfalse
                """.getBytes(StandardCharsets.UTF_8);
        byte[] text = """
                第一行：南环路 2024-01-02 开通
                第二行：北环路 2024-01-03 开通
                第三行：用于 TXT 行文本解析测试
                """.getBytes(StandardCharsets.UTF_8);
        byte[] json = """
                [
                  {"road_id":7,"name":"南环路","opened":"2024-01-02","active":true,"amount":123.45,"tags":["主干道","城区"]},
                  {"road_id":8,"name":"北环路","opened":"2024-01-03","active":false,"amount":67.89,"tags":["次干道"]}
                ]
                """.getBytes(StandardCharsets.UTF_8);
        byte[] jsonLines = """
                {"road_id":7,"name":"南环路","active":true,"length":12.5}
                {"road_id":8,"name":"北环路","active":false,"length":20.0,"district":"中心城区"}
                """.getBytes(StandardCharsets.UTF_8);

        writeIfMissing("roads.csv", csv);
        writeIfMissing("roads.tsv", tsv);
        writeIfMissing("notes.txt", text);
        writeIfMissing("roads.json", json);
        writeIfMissing("roads.jsonl", jsonLines);
        writeIfMissing("roads.xls", () -> spreadsheet(false));
        writeIfMissing("roads.xlsx", () -> spreadsheet(true));
        writeIfMissing("roads.parquet", this::parquet);
        writeIfMissing("roads.avro", this::avro);

        writeIfMissing("roads.csv.gz", () -> gzip(csv));
        writeIfMissing("roads.tsv.gz", () -> gzip(tsv));
        writeIfMissing("notes.txt.gz", () -> gzip(text));
        writeIfMissing("roads.jsonl.gz", () -> gzip(jsonLines));

        Files.writeString(OUTPUT_DIRECTORY.resolve("README.md"), """
                # 文件数据集手工测试样例

                目录覆盖当前可以真实解析的全部文件数据集格式：

                - `roads.csv`：CSV，UTF-8、逗号分隔、首行表头。
                - `roads.tsv`：TSV，UTF-8、制表符分隔、首行表头。
                - `notes.txt`：TXT，UTF-8 行文本。
                - `roads.json`：JSON，顶层数组，不需要 JSON Pointer。
                - `roads.jsonl`：JSONL，每行一个 JSON 对象。
                - `roads.xls`：Excel 97-2003，工作表名为 `Roads`。
                - `roads.xlsx`：Excel OOXML，工作表名为 `Roads`。
                - `roads.parquet`：Parquet，包含日期、时间戳、decimal、List 和 Map 字段。
                - `roads.avro`：Avro Object Container File，使用 Snappy 内部 codec。

                GZIP 支持组合也各保留一份：

                - `roads.csv.gz`
                - `roads.tsv.gz`
                - `notes.txt.gz`
                - `roads.jsonl.gz`

                生成器只创建缺失的样例文件，不覆盖目录中已经存在的数据文件。

                这些文件由 `ManualFileDatasetSampleDataGeneratorTest` 生成；需要重新生成时执行：

                ```bash
                ./mvnw -pl data-scalpel-admin -am test \\
                  -Dtest=ManualFileDatasetSampleDataGeneratorTest \\
                  -DgenerateManualFileDatasetSamples=true \\
                  -Dsurefire.failIfNoSpecifiedTests=false
                ```
                """, StandardCharsets.UTF_8);

        validateSamples();
    }

    private void writeIfMissing(String fileName, byte[] content) throws IOException {
        Path target = OUTPUT_DIRECTORY.resolve(fileName);
        if (Files.notExists(target)) {
            Files.write(target, content);
        }
    }

    private void writeIfMissing(String fileName, SampleContentSupplier contentSupplier) throws IOException {
        Path target = OUTPUT_DIRECTORY.resolve(fileName);
        if (Files.notExists(target)) {
            Files.write(target, contentSupplier.get());
        }
    }

    private byte[] gzip(byte[] content) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(content);
            gzipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private byte[] avro() throws IOException {
        Schema schema = new Schema.Parser().parse("""
                {
                  "type":"record", "name":"Road", "namespace":"cn.superhuang.data.scalpel.sample",
                  "fields":[
                    {"name":"name","type":"string"},
                    {"name":"road_id","type":"long"},
                    {"name":"opened","type":{"type":"int","logicalType":"date"}},
                    {"name":"amount","type":{"type":"bytes","logicalType":"decimal","precision":10,"scale":2}},
                    {"name":"tags","type":{"type":"array","items":"string"}},
                    {"name":"attributes","type":{"type":"map","values":"string"}},
                    {"name":"payload","type":"bytes"}
                  ]
                }
                """);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(schema))) {
            writer.setCodec(CodecFactory.snappyCodec());
            writer.create(schema, outputStream);
            GenericRecord record = new GenericData.Record(schema);
            record.put("name", "South Road");
            record.put("road_id", 7L);
            record.put("opened", (int) LocalDate.of(2024, 1, 2).toEpochDay());
            Schema amountSchema = schema.getField("amount").schema();
            record.put("amount", new Conversions.DecimalConversion().toBytes(
                    new BigDecimal("123.45"), amountSchema, amountSchema.getLogicalType()
            ));
            record.put("tags", List.of("main", "urban"));
            record.put("attributes", Map.of("district", "中心城区"));
            record.put("payload", ByteBuffer.wrap(new byte[]{1, 2, 3}));
            writer.append(record);
            writer.flush();
            return outputStream.toByteArray();
        }
    }

    private byte[] spreadsheet(boolean xlsx) throws IOException {
        try (Workbook workbook = xlsx ? new XSSFWorkbook() : new HSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Roads");
            Row header = sheet.createRow(0);
            String[] headers = {"road_id", "name", "opened", "active", "amount"};
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            appendSpreadsheetRoad(sheet, dateStyle, 1, 7, "南环路", LocalDate.of(2024, 1, 2), true, 123.45D);
            appendSpreadsheetRoad(sheet, dateStyle, 2, 8, "北环路", LocalDate.of(2024, 1, 3), false, 67.89D);
            appendSpreadsheetRoad(sheet, dateStyle, 3, 9, "东环路", LocalDate.of(2024, 1, 4), true, 88.00D);
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void appendSpreadsheetRoad(
            Sheet sheet,
            CellStyle dateStyle,
            int rowIndex,
            int roadId,
            String name,
            LocalDate opened,
            boolean active,
            double amount
    ) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(roadId);
        row.createCell(1).setCellValue(name);
        row.createCell(2).setCellValue(opened);
        row.getCell(2).setCellStyle(dateStyle);
        row.createCell(3).setCellValue(active);
        row.createCell(4).setCellValue(amount);
    }

    private byte[] parquet() throws IOException {
        MessageType schema = MessageTypeParser.parseMessageType("""
                message roads {
                  required int32 road_id;
                  required binary name (STRING);
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
        Path temporaryFile = Files.createTempFile("data-scalpel-manual-sample-", ".parquet");
        try {
            Files.deleteIfExists(temporaryFile);
            SimpleGroupFactory groups = new SimpleGroupFactory(schema);
            try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new LocalOutputFile(temporaryFile))
                    .withType(schema)
                    .build()) {
                Group south = groups.newGroup()
                        .append("road_id", 7)
                        .append("name", "南环路")
                        .append("opened", (int) LocalDate.of(2024, 1, 2).toEpochDay())
                        .append("observed_at", 1_704_190_200_000L)
                        .append("amount", 12345L);
                south.addGroup("tags").addGroup("list").append("element", "主干道");
                south.getGroup("tags", 0).addGroup("list").append("element", "城区");
                south.addGroup("attributes").addGroup("key_value")
                        .append("key", "district").append("value", "中心城区");
                writer.write(south);
                writer.write(groups.newGroup().append("road_id", 8).append("name", "北环路"));
            }
            return Files.readAllBytes(temporaryFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private void validateSamples() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        FileDatasetParsingConfiguration.Csv csv = new FileDatasetParsingConfiguration.Csv(
                "UTF-8", ",", FileRecordDelimiter.AUTO, "\"", "\\", true
        );
        FileDatasetParsingConfiguration.Csv tsv = new FileDatasetParsingConfiguration.Csv(
                "UTF-8", "\t", FileRecordDelimiter.AUTO, "\"", "\\", true
        );
        FileDatasetParsingConfiguration.Text text = new FileDatasetParsingConfiguration.Text(
                "UTF-8", FileRecordDelimiter.AUTO
        );
        FileDatasetParsingConfiguration.JsonLines jsonLines = new FileDatasetParsingConfiguration.JsonLines(
                "UTF-8", FileRecordDelimiter.AUTO
        );

        assertStreamSample(new CsvFileDatasetParser(), csv, "roads.csv", false);
        assertStreamSample(new CsvFileDatasetParser(), tsv, "roads.tsv", false);
        assertStreamSample(new TextFileDatasetParser(), text, "notes.txt", false);
        assertStreamSample(new JsonFileDatasetParser(objectMapper),
                new FileDatasetParsingConfiguration.Json("UTF-8", ""), "roads.json", false);
        assertStreamSample(new JsonLinesFileDatasetParser(objectMapper), jsonLines, "roads.jsonl", false);
        assertLocalFileSample(new XlsFileDatasetParser(),
                new FileDatasetParsingConfiguration.Spreadsheet("Roads", 0, 1), "roads.xls");
        assertLocalFileSample(new XlsxFileDatasetParser(),
                new FileDatasetParsingConfiguration.Spreadsheet("Roads", 0, 1), "roads.xlsx");
        assertLocalFileSample(new ParquetFileDatasetParser(objectMapper),
                new FileDatasetParsingConfiguration.Parquet(), "roads.parquet");
        assertStreamSample(new AvroFileDatasetParser(objectMapper),
                new FileDatasetParsingConfiguration.Avro(), "roads.avro", false);

        assertStreamSample(new CsvFileDatasetParser(), csv, "roads.csv.gz", true);
        assertStreamSample(new CsvFileDatasetParser(), tsv, "roads.tsv.gz", true);
        assertStreamSample(new TextFileDatasetParser(), text, "notes.txt.gz", true);
        assertStreamSample(new JsonLinesFileDatasetParser(objectMapper), jsonLines, "roads.jsonl.gz", true);
    }

    private void assertStreamSample(
            FileDatasetParser parser,
            FileDatasetParsingConfiguration configuration,
            String fileName,
            boolean gzipCompressed
    ) throws IOException {
        try (InputStream fileInput = Files.newInputStream(OUTPUT_DIRECTORY.resolve(fileName));
             InputStream parserInput = gzipCompressed ? new GZIPInputStream(fileInput) : fileInput) {
            FileDatasetParser.ParseResult result = parser.parse(
                    new FileDatasetParseSource.Stream(parserInput), configuration, 10
            );
            assertFalse(result.fields().isEmpty(), fileName + " 应解析出字段");
            assertFalse(result.rows().isEmpty(), fileName + " 应解析出样本记录");
        }
    }

    private void assertLocalFileSample(
            FileDatasetParser parser,
            FileDatasetParsingConfiguration configuration,
            String fileName
    ) throws IOException {
        FileDatasetParser.ParseResult result = parser.parse(
                new FileDatasetParseSource.LocalFile(OUTPUT_DIRECTORY.resolve(fileName)), configuration, 10
        );
        assertFalse(result.fields().isEmpty(), fileName + " 应解析出字段");
        assertFalse(result.rows().isEmpty(), fileName + " 应解析出样本记录");
    }

    @FunctionalInterface
    private interface SampleContentSupplier {

        byte[] get() throws IOException;
    }
}
