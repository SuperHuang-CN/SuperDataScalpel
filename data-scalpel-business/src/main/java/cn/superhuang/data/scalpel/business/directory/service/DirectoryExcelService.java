package cn.superhuang.data.scalpel.business.directory.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.web.response.DirectoryImportResultResponse;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DirectoryExcelService {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String FILE_MARKER = "DATASCALPEL_DIRECTORY_IMPORT";
    public static final int FORMAT_VERSION = 1;
    public static final List<String> DIRECTORY_HEADERS = List.of(
            "行标识*", "父行标识", "目录名称*", "排序", "说明"
    );

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_ROWS = 5_000;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ThreadLocal<DataFormatter> DATA_FORMATTER = ThreadLocal.withInitial(
            () -> new DataFormatter(Locale.CHINA)
    );

    private final DirectoryService directoryService;

    public DirectoryExcelService(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    public DirectoryExcelFile template() {
        return new DirectoryExcelFile("DataScalpel-目录导入模板.xlsx", writeWorkbook(List.of()));
    }

    public DirectoryExcelFile export(DirectoryScope scope) {
        String fileName = "DataScalpel-" + scope.name() + "-目录-"
                + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".xlsx";
        return new DirectoryExcelFile(fileName, writeWorkbook(directoryService.exportRows(scope)));
    }

    public DirectoryImportResultResponse importDirectories(DirectoryScope scope, MultipartFile file) {
        return directoryService.importRows(scope, readWorkbook(file));
    }

    private byte[] writeWorkbook(List<DirectoryImportRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            WorkbookStyles styles = styles(workbook);
            createInstructionsSheet(workbook, styles);
            Sheet sheet = workbook.createSheet("目录");
            sheet.setDisplayGridlines(false);
            Row header = sheet.createRow(0);
            header.setHeightInPoints(26);
            for (int column = 0; column < DIRECTORY_HEADERS.size(); column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(DIRECTORY_HEADERS.get(column));
                cell.setCellStyle(DIRECTORY_HEADERS.get(column).endsWith("*")
                        ? styles.requiredHeader() : styles.header());
            }
            sheet.setColumnWidth(0, 22 * 256);
            sheet.setColumnWidth(1, 22 * 256);
            sheet.setColumnWidth(2, 32 * 256);
            sheet.setColumnWidth(3, 12 * 256);
            sheet.setColumnWidth(4, 64 * 256);
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 4));

            int rowIndex = 1;
            for (DirectoryImportRow directory : rows) {
                Row row = sheet.createRow(rowIndex++);
                writeText(row, 0, directory.rowKey(), styles.text());
                writeText(row, 1, directory.parentRowKey(), styles.text());
                writeText(row, 2, directory.name(), styles.text());
                Cell sortCell = row.createCell(3);
                sortCell.setCellValue(directory.sortOrder());
                sortCell.setCellStyle(styles.integer());
                writeText(row, 4, directory.description(), styles.description());
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("生成目录 Excel 失败", exception);
        }
    }

    private static void createInstructionsSheet(XSSFWorkbook workbook, WorkbookStyles styles) {
        Sheet sheet = workbook.createSheet("说明");
        sheet.setDisplayGridlines(false);
        String[][] rows = {
                {"文件标识", FILE_MARKER},
                {"格式版本", String.valueOf(FORMAT_VERSION)},
                {"用途", "批量导入或导出当前页面对应业务范围的目录树；目录范围由导入页面决定。"},
                {"层级关系", "每行使用独立行标识；父行标识为空表示顶级目录，否则填写同一文件中另一行的行标识。"},
                {"合并规则", "同一父目录下按名称（忽略大小写）匹配；不存在则新增，已存在则更新名称、排序和说明。"},
                {"安全规则", "导入不会删除文件中未出现的现有目录，可重复导入同一文件。"},
                {"填写规则", "带 * 的列必填；行标识在文件内必须唯一；排序为空时按 0 处理。"},
                {"校验规则", "导入前会完整校验父行、循环引用、同级重名、字段长度和最多 5000 条目录。"},
                {"字段限制", "目录名称最多 100 个字符，说明最多 500 个字符，只支持 .xlsx 文件且不超过 10 MB。"}
        };
        for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            row.setHeightInPoints(rowIndex < 2 ? 24 : 34);
            writeText(row, 0, rows[rowIndex][0], styles.instructionLabel());
            writeText(row, 1, rows[rowIndex][1], styles.instructionValue());
        }
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 108 * 256);
    }

    private List<DirectoryImportRow> readWorkbook(MultipartFile file) {
        validateFile(file);
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            validateWorkbookMarker(workbook);
            Sheet sheet = requireSheet(workbook, "目录");
            requireHeaders(sheet);
            if (sheet.getLastRowNum() > MAX_ROWS) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "单次最多导入 5000 条目录");
            }
            Map<String, DirectoryImportRow> rowsByKey = new LinkedHashMap<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                String rowKey = formattedCell(row, 0).trim();
                String parentRowKey = normalizeOptional(formattedCell(row, 1));
                String name = formattedCell(row, 2).trim();
                String sortOrder = formattedCell(row, 3).trim();
                String description = normalizeOptional(formattedCell(row, 4));
                if (rowKey.isEmpty() && parentRowKey == null && name.isEmpty()
                        && sortOrder.isEmpty() && description == null) {
                    continue;
                }
                int excelRow = rowIndex + 1;
                if (rowKey.isEmpty()) {
                    throw invalidRow(excelRow, "行标识不能为空");
                }
                if (rowKey.length() > 100) {
                    throw invalidRow(excelRow, "行标识不能超过 100 个字符");
                }
                if (name.isEmpty()) {
                    throw invalidRow(excelRow, "目录名称不能为空");
                }
                if (name.length() > 100) {
                    throw invalidRow(excelRow, "目录名称不能超过 100 个字符");
                }
                if (description != null && description.length() > 500) {
                    throw invalidRow(excelRow, "说明不能超过 500 个字符");
                }
                int parsedSortOrder = parseSortOrder(sortOrder, excelRow);
                DirectoryImportRow directory = new DirectoryImportRow(
                        rowKey, parentRowKey, name, parsedSortOrder, description
                );
                if (rowsByKey.putIfAbsent(rowKey, directory) != null) {
                    throw invalidRow(excelRow, "行标识“" + rowKey + "”重复");
                }
            }
            if (rowsByKey.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录 Excel 中没有可导入的数据");
            }
            validateReferences(rowsByKey);
            return topologicalOrder(rowsByKey);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法读取目录 Excel，请使用系统模板", exception);
        }
    }

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要导入的目录 Excel");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "目录 Excel 不能超过 10 MB");
        }
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!fileName.endsWith(".xlsx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持 .xlsx 目录文件");
        }
    }

    private static void validateWorkbookMarker(Workbook workbook) {
        Sheet instructions = requireSheet(workbook, "说明");
        if (!FILE_MARKER.equals(formattedCell(instructions.getRow(0), 1).trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 文件标识不正确，请使用系统目录模板");
        }
        String version = formattedCell(instructions.getRow(1), 1).trim();
        if (!String.valueOf(FORMAT_VERSION).equals(version)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持该目录 Excel 格式版本：" + version);
        }
    }

    private static Sheet requireSheet(Workbook workbook, String name) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 缺少工作表：“" + name + "”");
        }
        return sheet;
    }

    private static void requireHeaders(Sheet sheet) {
        Row header = sheet.getRow(0);
        for (int column = 0; column < DIRECTORY_HEADERS.size(); column++) {
            String actual = formattedCell(header, column).trim();
            if (!DIRECTORY_HEADERS.get(column).equals(actual)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "目录工作表第 " + (column + 1) + " 列应为“" + DIRECTORY_HEADERS.get(column) + "”"
                );
            }
        }
    }

    private static void validateReferences(Map<String, DirectoryImportRow> rowsByKey) {
        Map<String, String> siblingNames = new HashMap<>();
        for (DirectoryImportRow row : rowsByKey.values()) {
            if (row.parentRowKey() != null && !rowsByKey.containsKey(row.parentRowKey())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "目录“" + row.name() + "”引用的父行标识“" + row.parentRowKey() + "”不存在"
                );
            }
            String siblingKey = (row.parentRowKey() == null ? "<root>" : row.parentRowKey())
                    + "\u0000" + row.name().toLowerCase(Locale.ROOT);
            String duplicate = siblingNames.putIfAbsent(siblingKey, row.name());
            if (duplicate != null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "同一父目录下存在重名目录：“" + duplicate + "”与“" + row.name() + "”"
                );
            }
        }
    }

    private static List<DirectoryImportRow> topologicalOrder(Map<String, DirectoryImportRow> rowsByKey) {
        Map<String, VisitState> states = new HashMap<>();
        List<DirectoryImportRow> ordered = new ArrayList<>(rowsByKey.size());
        for (String rowKey : rowsByKey.keySet()) {
            visit(rowKey, rowsByKey, states, ordered);
        }
        return List.copyOf(ordered);
    }

    private static void visit(
            String rowKey,
            Map<String, DirectoryImportRow> rowsByKey,
            Map<String, VisitState> states,
            List<DirectoryImportRow> ordered
    ) {
        VisitState state = states.get(rowKey);
        if (state == VisitState.VISITED) {
            return;
        }
        if (state == VisitState.VISITING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录层级存在循环引用，涉及行标识：“" + rowKey + "”");
        }
        states.put(rowKey, VisitState.VISITING);
        DirectoryImportRow row = rowsByKey.get(rowKey);
        if (row.parentRowKey() != null) {
            visit(row.parentRowKey(), rowsByKey, states, ordered);
        }
        states.put(rowKey, VisitState.VISITED);
        ordered.add(row);
    }

    private static int parseSortOrder(String value, int excelRow) {
        if (value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalidRow(excelRow, "排序必须为整数");
        }
    }

    private static ResponseStatusException invalidRow(int excelRow, String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "目录工作表第 " + excelRow + " 行：" + detail);
    }

    private static String formattedCell(Row row, int column) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : DATA_FORMATTER.get().formatCellValue(cell);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static WorkbookStyles styles(XSSFWorkbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.WHITE.getIndex());

        CellStyle header = workbook.createCellStyle();
        header.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.INDIGO.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setFont(headerFont);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle requiredHeader = workbook.createCellStyle();
        requiredHeader.cloneStyleFrom(header);
        requiredHeader.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.DARK_BLUE.getIndex());

        CellStyle text = workbook.createCellStyle();
        text.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle integer = workbook.createCellStyle();
        integer.setVerticalAlignment(VerticalAlignment.CENTER);
        integer.setAlignment(HorizontalAlignment.RIGHT);
        integer.setDataFormat(workbook.createDataFormat().getFormat("0"));

        CellStyle description = workbook.createCellStyle();
        description.cloneStyleFrom(text);
        description.setWrapText(true);

        Font labelFont = workbook.createFont();
        labelFont.setBold(true);
        labelFont.setColor(org.apache.poi.ss.usermodel.IndexedColors.INDIGO.getIndex());
        CellStyle instructionLabel = workbook.createCellStyle();
        instructionLabel.setFont(labelFont);
        instructionLabel.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.PALE_BLUE.getIndex());
        instructionLabel.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        instructionLabel.setVerticalAlignment(VerticalAlignment.CENTER);
        instructionLabel.setBorderBottom(BorderStyle.THIN);
        instructionLabel.setBottomBorderColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());

        CellStyle instructionValue = workbook.createCellStyle();
        instructionValue.setWrapText(true);
        instructionValue.setVerticalAlignment(VerticalAlignment.CENTER);
        instructionValue.setBorderBottom(BorderStyle.THIN);
        instructionValue.setBottomBorderColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
        return new WorkbookStyles(header, requiredHeader, text, integer, description, instructionLabel, instructionValue);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private record WorkbookStyles(
            CellStyle header,
            CellStyle requiredHeader,
            CellStyle text,
            CellStyle integer,
            CellStyle description,
            CellStyle instructionLabel,
            CellStyle instructionValue
    ) {
    }
}
