package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryImportFormat;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.util.XMLHelper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Component
class DataEntryImportFileReader {

    static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    static final int MAX_ROWS = 100_000;
    private static final String DATA_SHEET = "数据填报";

    DataEntryImportFormat format(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要导入的 Excel 或 CSV 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "导入文件不能超过 50 MB");
        }
        String name = originalFileName(file).toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx")) return DataEntryImportFormat.XLSX;
        if (name.endsWith(".csv")) return DataEntryImportFormat.CSV;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持 .xlsx 和 .csv 导入文件");
    }

    String originalFileName(MultipartFile file) {
        String value = file == null ? null : file.getOriginalFilename();
        if (value == null || value.isBlank()) return "data-entry-import";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return (slash >= 0 ? normalized.substring(slash + 1) : normalized).trim();
    }

    void read(
            MultipartFile file,
            DataEntryImportFormat format,
            List<DataModelField> fields,
            Consumer<RawRow> consumer
    ) {
        try {
            if (format == DataEntryImportFormat.CSV) readCsv(file, fields, consumer);
            else readXlsx(file, fields, consumer);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (CharacterCodingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV 必须使用 UTF-8 编码", exception);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导入文件无法解析或格式已损坏", exception);
        }
    }

    private void readCsv(MultipartFile file, List<DataModelField> fields, Consumer<RawRow> consumer) throws IOException {
        try (InputStream input = file.getInputStream()) {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            PushbackReader reader = new PushbackReader(new BufferedReader(new InputStreamReader(input, decoder)), 2);
            List<CsvRecord> headers = new ArrayList<>(2);
            CsvState state = new CsvState(reader);
            CsvRecord record;
            int rows = 0;
            while ((record = state.next()) != null) {
                if (headers.size() < 2) {
                    headers.add(record);
                    if (headers.size() == 2) validateHeaders(headers.get(0).cells(), headers.get(1).cells(), fields);
                    continue;
                }
                if (blank(record.cells())) continue;
                if (++rows > MAX_ROWS) {
                    throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "单次导入最多支持 100000 条数据");
                }
                rejectExtraValues(record.rowNumber(), record.cells(), fields.size());
                consumer.accept(row(record.rowNumber(), record.cells(), headerCodes(headers.get(1).cells(), fields.size())));
            }
            if (headers.size() < 2) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导入文件必须包含名称和字段编码两行表头");
            }
        }
    }

    private void readXlsx(MultipartFile file, List<DataModelField> fields, Consumer<RawRow> consumer) throws Exception {
        try (InputStream input = file.getInputStream(); OPCPackage workbook = OPCPackage.open(input)) {
            XSSFReader reader = new XSSFReader(workbook, true);
            StylesTable styles = reader.getStylesTable();
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(workbook);
            XSSFReader.SheetIterator sheets = reader.getSheetIterator();
            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    if (!DATA_SHEET.equals(sheets.getSheetName())) continue;
                    XlsxRows rows = new XlsxRows(fields, consumer);
                    XMLReader xmlReader = XMLHelper.newXMLReader();
                    xmlReader.setContentHandler(new FormulaAwareSheetHandler(
                            styles, strings, rows, new DataFormatter(Locale.ROOT), rows
                    ));
                    xmlReader.parse(new InputSource(sheet));
                    rows.finish();
                    return;
                }
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 缺少“数据填报”工作表，请使用系统模板");
        }
    }

    private static RawRow row(int rowNumber, List<RawCell> cells, List<String> fieldCodes) {
        Map<String, RawCell> values = new LinkedHashMap<>();
        for (int index = 0; index < fieldCodes.size(); index++) {
            values.put(fieldCodes.get(index), index < cells.size() ? cells.get(index) : RawCell.empty());
        }
        return new RawRow(rowNumber, values);
    }

    private static List<String> headerCodes(List<RawCell> cells, int fieldCount) {
        List<String> result = new ArrayList<>(fieldCount);
        for (int index = 0; index < fieldCount; index++) result.add(cells.get(index).value().trim());
        return List.copyOf(result);
    }

    private static void validateHeaders(List<RawCell> names, List<RawCell> codes, List<DataModelField> fields) {
        int lastName = lastNonBlank(names);
        int lastCode = lastNonBlank(codes);
        if (lastName != lastCode || lastCode + 1 != fields.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称行与字段编码行的有效列数必须和当前模型字段数一致");
        }
        Set<String> actual = new HashSet<>();
        for (int index = 0; index <= lastCode; index++) {
            if (names.get(index).formula() || names.get(index).error()
                    || codes.get(index).formula() || codes.get(index).error()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导入表头不能使用公式或错误单元格");
            }
            String code = codes.get(index).value() == null ? "" : codes.get(index).value().trim();
            if (names.get(index).value() == null || names.get(index).value().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段名称表头不能为空：第 " + (index + 1) + " 列");
            }
            if (code.isEmpty() || !actual.add(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段编码表头不能为空或重复：第 " + (index + 1) + " 列");
            }
        }
        Set<String> expected = new HashSet<>(fields.stream().map(DataModelField::getCode).toList());
        if (!actual.equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(actual);
            Set<String> unknown = new HashSet<>(actual);
            unknown.removeAll(expected);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导入字段与当前模型不一致；缺少：" + missing + "，未知：" + unknown);
        }
    }

    private static void rejectExtraValues(int rowNumber, List<RawCell> cells, int expectedColumns) {
        for (int index = expectedColumns; index < cells.size(); index++) {
            RawCell cell = cells.get(index);
            if (cell.value() != null && !cell.value().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "第 " + rowNumber + " 行存在表头之外的非空列");
            }
        }
    }

    private static int lastNonBlank(List<RawCell> cells) {
        for (int index = cells.size() - 1; index >= 0; index--) {
            if (cells.get(index).value() != null && !cells.get(index).value().isBlank()) return index;
        }
        return -1;
    }

    private static boolean blank(List<RawCell> cells) {
        return cells.stream().allMatch(cell -> !cell.explicitEmpty() && !cell.formula() && !cell.error()
                && (cell.value() == null || cell.value().isEmpty()));
    }

    record RawRow(int rowNumber, Map<String, RawCell> values) {
        RawRow {
            values = Map.copyOf(values);
        }
    }

    record RawCell(
            String value,
            boolean explicitEmpty,
            boolean formula,
            boolean numeric,
            boolean error,
            boolean excelDate
    ) {
        static RawCell empty() {
            return new RawCell(null, false, false, false, false, false);
        }
    }

    private record CsvRecord(int rowNumber, List<RawCell> cells) {
    }

    private static final class CsvState {
        private final PushbackReader reader;
        private int physicalLine = 1;
        private boolean firstCharacter = true;

        private CsvState(PushbackReader reader) {
            this.reader = reader;
        }

        private CsvRecord next() throws IOException {
            List<RawCell> cells = new ArrayList<>();
            StringBuilder value = new StringBuilder();
            boolean quoted = false;
            boolean started = false;
            int startLine = physicalLine;
            while (true) {
                int read = reader.read();
                if (firstCharacter) {
                    firstCharacter = false;
                    if (read == '\ufeff') read = reader.read();
                }
                if (read < 0) {
                    if (!started && cells.isEmpty() && value.isEmpty()) return null;
                    cells.add(csvCell(value.toString(), quoted));
                    return new CsvRecord(startLine, List.copyOf(cells));
                }
                char character = (char) read;
                if (!started && character == '"') {
                    quoted = true;
                    started = true;
                    readQuoted(value);
                    int delimiter = reader.read();
                    if (delimiter == ',') {
                        cells.add(csvCell(value.toString(), true));
                        value.setLength(0);
                        quoted = false;
                        started = false;
                        continue;
                    }
                    if (delimiter == '\r' || delimiter == '\n' || delimiter < 0) {
                        cells.add(csvCell(value.toString(), true));
                        consumeLineEnd(delimiter);
                        return new CsvRecord(startLine, List.copyOf(cells));
                    }
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV 第 " + physicalLine + " 行的引号字段后存在非法字符");
                }
                started = true;
                if (character == '"') {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV 第 " + physicalLine + " 行的非引号字段包含非法引号");
                }
                if (character == ',') {
                    cells.add(csvCell(value.toString(), quoted));
                    value.setLength(0);
                    quoted = false;
                    started = false;
                } else if (character == '\r' || character == '\n') {
                    cells.add(csvCell(value.toString(), quoted));
                    consumeLineEnd(character);
                    return new CsvRecord(startLine, List.copyOf(cells));
                } else {
                    value.append(character);
                }
            }
        }

        private void readQuoted(StringBuilder value) throws IOException {
            while (true) {
                int read = reader.read();
                if (read < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV 引号字段未闭合");
                char character = (char) read;
                if (character == '"') {
                    int next = reader.read();
                    if (next == '"') value.append('"');
                    else {
                        if (next >= 0) reader.unread(next);
                        return;
                    }
                } else {
                    value.append(character);
                    if (character == '\n') physicalLine++;
                    else if (character == '\r') {
                        int next = reader.read();
                        if (next != '\n' && next >= 0) reader.unread(next);
                        else if (next == '\n') value.append('\n');
                        physicalLine++;
                    }
                }
            }
        }

        private void consumeLineEnd(int delimiter) throws IOException {
            if (delimiter == '\r') {
                int next = reader.read();
                if (next != '\n' && next >= 0) reader.unread(next);
            }
            if (delimiter >= 0) physicalLine++;
        }

        private static RawCell csvCell(String value, boolean quoted) {
            if (!quoted && value.isEmpty()) return RawCell.empty();
            return new RawCell(value, quoted && value.isEmpty(), false, false, false, false);
        }
    }

    private static final class XlsxRows implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final List<DataModelField> fields;
        private final Consumer<RawRow> consumer;
        private final List<List<RawCell>> headers = new ArrayList<>(2);
        private final Map<Integer, RawCell> current = new LinkedHashMap<>();
        private int currentRow;
        private int rows;
        private List<String> fieldCodes = List.of();
        private String currentCellReference;
        private boolean currentFormula;
        private boolean currentNumeric;
        private boolean currentError;
        private boolean currentDate;
        private String currentRawNumeric;

        private XlsxRows(List<DataModelField> fields, Consumer<RawRow> consumer) {
            this.fields = fields;
            this.consumer = consumer;
        }

        @Override
        public void startRow(int rowNum) {
            current.clear();
            currentRow = rowNum + 1;
        }

        @Override
        public void endRow(int rowNum) {
            if (currentFormula && currentCellReference != null) {
                current.putIfAbsent(columnIndex(currentCellReference), new RawCell(
                        null, false, true, currentNumeric, currentError, currentDate
                ));
            }
            int maximum = current.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
            List<RawCell> cells = new ArrayList<>();
            for (int index = 0; index <= maximum; index++) cells.add(current.getOrDefault(index, RawCell.empty()));
            if (headers.size() < 2) {
                headers.add(List.copyOf(cells));
                if (headers.size() == 2) {
                    validateHeaders(headers.get(0), headers.get(1), fields);
                    fieldCodes = headerCodes(headers.get(1), fields.size());
                }
                return;
            }
            if (blank(cells)) return;
            if (++rows > MAX_ROWS) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "单次导入最多支持 100000 条数据");
            rejectExtraValues(currentRow, cells, fields.size());
            consumer.accept(row(currentRow, cells, fieldCodes));
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = columnIndex(cellReference);
            String value = currentDate && currentRawNumeric != null
                    ? org.apache.poi.ss.usermodel.DateUtil.getLocalDateTime(Double.parseDouble(currentRawNumeric)).toString()
                    : formattedValue;
            current.put(column, new RawCell(
                    value == null || value.isEmpty() ? null : value,
                    false,
                    currentFormula,
                    currentNumeric,
                    currentError,
                    currentDate
            ));
        }

        private void cellStarted(String reference, boolean numeric, boolean error, boolean date) {
            if (currentFormula && currentCellReference != null) {
                current.putIfAbsent(columnIndex(currentCellReference), new RawCell(
                        null, false, true, currentNumeric, currentError, currentDate
                ));
            }
            currentCellReference = reference;
            currentFormula = false;
            currentNumeric = numeric;
            currentError = error;
            currentDate = date;
            currentRawNumeric = null;
        }

        private void formula() {
            currentFormula = true;
        }

        private void rawNumeric(String value) {
            currentRawNumeric = value;
        }

        private void finish() {
            if (headers.size() < 2) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Excel 必须包含名称和字段编码两行表头");
        }

        private static int columnIndex(String reference) {
            int result = 0;
            int index = 0;
            while (index < reference.length() && Character.isLetter(reference.charAt(index))) {
                result = result * 26 + Character.toUpperCase(reference.charAt(index)) - 'A' + 1;
                index++;
            }
            return result - 1;
        }
    }

    private static final class FormulaAwareSheetHandler extends XSSFSheetXMLHandler {
        private final XlsxRows rows;
        private final StylesTable styles;
        private boolean inValue;
        private final StringBuilder rawValue = new StringBuilder();

        private FormulaAwareSheetHandler(
                StylesTable styles,
                ReadOnlySharedStringsTable strings,
                SheetContentsHandler sheetContentsHandler,
                DataFormatter formatter,
                XlsxRows rows
        ) {
            super(styles, strings, sheetContentsHandler, formatter, false);
            this.rows = rows;
            this.styles = styles;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            String name = localName == null || localName.isEmpty() ? qName : localName;
            if ("c".equals(name)) {
                String type = attributes.getValue("t");
                boolean numeric = type == null || "n".equals(type);
                boolean error = "e".equals(type);
                boolean date = numeric && isDateStyle(attributes.getValue("s"));
                rows.cellStarted(attributes.getValue("r"), numeric, error, date);
            } else if ("f".equals(name)) {
                rows.formula();
            } else if ("v".equals(name)) {
                inValue = true;
                rawValue.setLength(0);
            }
            super.startElement(uri, localName, qName, attributes);
        }

        @Override
        public void characters(char[] characters, int start, int length) throws SAXException {
            if (inValue) rawValue.append(characters, start, length);
            super.characters(characters, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            String name = localName == null || localName.isEmpty() ? qName : localName;
            if ("v".equals(name)) {
                rows.rawNumeric(rawValue.toString());
                inValue = false;
            }
            super.endElement(uri, localName, qName);
        }

        private boolean isDateStyle(String styleIndex) {
            if (styleIndex == null || styleIndex.isBlank()) return false;
            try {
                var style = styles.getStyleAt(Integer.parseInt(styleIndex));
                return DateUtil.isADateFormat(style.getDataFormat(), style.getDataFormatString());
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }
}
