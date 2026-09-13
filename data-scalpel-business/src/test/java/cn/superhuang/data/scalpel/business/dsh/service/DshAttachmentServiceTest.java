package cn.superhuang.data.scalpel.business.dsh.service;

import org.junit.jupiter.api.Test;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import tools.jackson.databind.json.JsonMapper;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class DshAttachmentServiceTest {
    private final DshAttachmentService service = new DshAttachmentService(null, JsonMapper.builder().build());
    @Test void readsBothExcelFormatsWithSheetsCellAddressesAndCachedFormulaValues() throws Exception {
        for (boolean xlsx : new boolean[]{true,false}) try (Workbook book = xlsx ? new XSSFWorkbook() : new HSSFWorkbook()) {
            var sheet = book.createSheet("数据一"); var row = sheet.createRow(2);
            row.createCell(0).setCellValue("杭州"); row.createCell(2).setCellValue(21);
            var formula=row.createCell(3); formula.setCellFormula("C3*2");
            book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula);
            book.createSheet("空表"); var output=new ByteArrayOutputStream();book.write(output);
            String text=service.extract(xlsx?"xlsx":"xls",output.toByteArray());
            assertThat(text).contains("数据一","空表","\"A3\":\"杭州\"","\"C3\":\"21\"","\"D3\":\"42\"");
            assertThat(text).doesNotContain("\"B3\"");
        }
    }
    @Test void rejectsInvalidFilesBinaryTextAndOversizedExtractionInsteadOfSendingPartialData() {
        assertThatThrownBy(()->service.extract("xlsx",new byte[]{1,2,3})).hasMessageContaining("Excel");
        assertThatThrownBy(()->service.extract("txt",new byte[]{0,1})).hasMessageContaining("UTF-8");
        assertThatThrownBy(()->service.extract("txt",new byte[]{(byte)0xff})).hasMessageContaining("UTF-8");
        assertThatThrownBy(()->service.extract("txt","x".repeat(1048577).getBytes(StandardCharsets.UTF_8))).hasMessageContaining("1 MiB");
        assertThatThrownBy(()->DshAttachmentService.decode("YQ")).hasMessageContaining("Base64");
        assertThat(service.extract("txt","中文\n\t123".getBytes(StandardCharsets.UTF_8))).isEqualTo("中文\n\t123");
    }
}
