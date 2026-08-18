package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryForm;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryImportFormat;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryModelLookup;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataEntryImportTemplateService {

    private static final String XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    private final DataEntryFormService formService;
    private final DataEntryHealthService healthService;

    public DataEntryImportTemplateService(DataEntryFormService formService, DataEntryHealthService healthService) {
        this.formService = formService;
        this.healthService = healthService;
    }

    public DataEntryImportFile template(UUID formId, DataEntryImportFormat format) {
        DataEntryForm form = formService.requireForm(formId);
        DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
        if (snapshot.model() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标模型已不存在，不能生成导入模板");
        }
        if (snapshot.fields().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标模型没有字段，不能生成导入模板");
        }
        String baseName = "DataScalpel-" + safeFileName(snapshot.model().getCode()) + "-填报模板";
        return format == DataEntryImportFormat.CSV
                ? new DataEntryImportFile(baseName + ".csv", CSV_CONTENT_TYPE, csv(snapshot.fields()))
                : new DataEntryImportFile(baseName + ".xlsx", XLSX_CONTENT_TYPE, xlsx(snapshot));
    }

    private static byte[] csv(List<DataModelField> fields) {
        StringBuilder content = new StringBuilder("\ufeff");
        content.append(fields.stream().map(DataModelField::getName).map(DataEntryImportTemplateService::csvCell)
                .collect(Collectors.joining(","))).append("\r\n");
        content.append(fields.stream().map(DataModelField::getCode).map(DataEntryImportTemplateService::csvCell)
                .collect(Collectors.joining(","))).append("\r\n");
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] xlsx(DataEntryMetadataSnapshot snapshot) {
        Map<UUID, DataEntryModelLookup> lookups = snapshot.lookups().stream()
                .collect(Collectors.toMap(DataEntryModelLookup::getTargetFieldId, Function.identity()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle nameStyle = workbook.createCellStyle();
            nameStyle.setFont(headerFont);
            nameStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            nameStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle codeStyle = workbook.createCellStyle();
            codeStyle.setFont(headerFont);
            codeStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            codeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));

            Sheet data = workbook.createSheet("数据填报");
            data.createFreezePane(0, 2);
            Row names = data.createRow(0);
            Row codes = data.createRow(1);
            for (int index = 0; index < snapshot.fields().size(); index++) {
                DataModelField field = snapshot.fields().get(index);
                var name = names.createCell(index);
                name.setCellValue(field.getName());
                name.setCellStyle(nameStyle);
                var code = codes.createCell(index);
                code.setCellValue(field.getCode());
                code.setCellStyle(codeStyle);
                data.setColumnWidth(index, Math.min(60, Math.max(14, Math.max(field.getName().length(), field.getCode().length()) + 4)) * 256);
                if (requiresTextCell(field)) data.setDefaultColumnStyle(index, textStyle);
            }
            data.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, snapshot.fields().size() - 1));

            Sheet instructions = workbook.createSheet("说明");
            String[] headers = {"字段名称", "字段编码", "平台类型", "必填", "主键", "输入说明", "字段说明"};
            Row header = instructions.createRow(0);
            for (int index = 0; index < headers.length; index++) {
                var cell = header.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(codeStyle);
            }
            for (int index = 0; index < snapshot.fields().size(); index++) {
                DataModelField field = snapshot.fields().get(index);
                Row row = instructions.createRow(index + 1);
                row.createCell(0).setCellValue(field.getName());
                row.createCell(1).setCellValue(field.getCode());
                row.createCell(2).setCellValue(typeDescription(field));
                row.createCell(3).setCellValue(!field.isNullable() || field.isPrimaryKey() ? "是" : "否");
                row.createCell(4).setCellValue(field.isPrimaryKey() ? "是" : "否");
                row.createCell(5).setCellValue(inputDescription(field, lookups.get(field.getId())));
                row.createCell(6).setCellValue(field.getDescription() == null ? "" : field.getDescription());
            }
            int[] widths = {22, 22, 20, 10, 10, 52, 42};
            for (int index = 0; index < widths.length; index++) instructions.setColumnWidth(index, widths[index] * 256);
            instructions.createFreezePane(0, 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成数据填报 Excel 模板失败", exception);
        }
    }

    private static boolean requiresTextCell(DataModelField field) {
        return field.getFieldType() == PlatformDataType.STRING
                || field.getFieldType() == PlatformDataType.LONG
                || field.getFieldType() == PlatformDataType.DECIMAL
                || field.getFieldType() == PlatformDataType.DATE
                || field.getFieldType() == PlatformDataType.TIMESTAMP
                || field.getFieldType() == PlatformDataType.TIMESTAMP_NTZ;
    }

    private static String typeDescription(DataModelField field) {
        if (field.getFieldType() == PlatformDataType.STRING && field.getLength() != null) {
            return "STRING(" + field.getLength() + ")";
        }
        if (field.getFieldType() == PlatformDataType.DECIMAL) {
            return "DECIMAL(" + field.getPrecision() + "," + field.getScale() + ")";
        }
        return field.getFieldType().name();
    }

    private static String inputDescription(DataModelField field, DataEntryModelLookup lookup) {
        if (field.getStandardDictionaryId() != null) return "填写码项 code；只能使用当前启用的码项";
        if (lookup != null) return "填写关联来源模型的单字段业务主键值";
        return switch (field.getFieldType()) {
            case DATE -> "ISO 日期，例如 2026-08-12";
            case TIMESTAMP -> "带时区 ISO 时间，例如 2026-08-12T10:30:00+08:00";
            case TIMESTAMP_NTZ -> "不带时区本地时间，例如 2026-08-12T10:30:00";
            case BOOLEAN -> "true 或 false";
            case LONG, DECIMAL -> "请按文本填写，避免 Excel 数字精度损失";
            case STRING -> "空单元格表示 null；精确填写 \"\" 表示空字符串";
            default -> "按平台类型填写标量值";
        };
    }

    private static String csvCell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
