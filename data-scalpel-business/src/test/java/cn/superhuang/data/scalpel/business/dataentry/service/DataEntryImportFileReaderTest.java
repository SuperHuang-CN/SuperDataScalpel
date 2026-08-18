package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryImportFormat;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataEntryImportFileReaderTest {

    private final DataEntryImportFileReader reader = new DataEntryImportFileReader();
    private final List<DataModelField> fields = List.of(
            field("id", "编号", PlatformDataType.INTEGER, false, true, 0),
            field("name", "名称", PlatformDataType.STRING, true, false, 1)
    );

    @Test
    void csvUsesCodeHeaderOrderAndPreservesQuotedEmptyString() {
        byte[] content = ("\ufeff名称,编号\r\nname,id\r\n\"\",1\r\n\" 含空格 \",2\r\n")
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "entry.csv", "text/csv", content);
        List<DataEntryImportFileReader.RawRow> rows = new ArrayList<>();

        reader.read(file, DataEntryImportFormat.CSV, fields, rows::add);

        assertEquals(2, rows.size());
        assertEquals("1", rows.getFirst().values().get("id").value());
        assertTrue(rows.getFirst().values().get("name").explicitEmpty());
        assertEquals(" 含空格 ", rows.get(1).values().get("name").value());
    }

    @Test
    void xlsxReportsFormulaCellsWithoutEvaluatingThem() throws Exception {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("数据填报");
            sheet.createRow(0).createCell(0).setCellValue("编号");
            sheet.getRow(0).createCell(1).setCellValue("名称");
            sheet.createRow(1).createCell(0).setCellValue("id");
            sheet.getRow(1).createCell(1).setCellValue("name");
            sheet.createRow(2).createCell(0).setCellValue(1);
            sheet.getRow(2).createCell(1).setCellFormula("\"示例\"");
            workbook.write(output);
            content = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "entry.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content
        );
        List<DataEntryImportFileReader.RawRow> rows = new ArrayList<>();

        reader.read(file, DataEntryImportFormat.XLSX, fields, rows::add);

        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().values().get("name").formula());
        assertFalse(rows.getFirst().values().get("id").formula());
    }

    private static DataModelField field(
            String code,
            String name,
            PlatformDataType type,
            boolean nullable,
            boolean primaryKey,
            int sortOrder
    ) {
        return DataModelField.create(
                UUID.randomUUID(), code, name, type,
                type == PlatformDataType.STRING ? 100 : null,
                null, null, nullable, primaryKey, sortOrder, null
        );
    }
}
