package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Streams XLSX worksheets with POI's event APIs; no workbook object is retained in memory. */
@Component
public class XlsxFileDatasetParser implements FileDatasetParser {

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.XLSX;
    }

    @Override
    public FileDatasetParserInputMode inputMode() {
        return FileDatasetParserInputMode.LOCAL_FILE;
    }

    @Override
    public ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.Spreadsheet spreadsheet)) {
            throw new FileDatasetParsingException("XLSX 解析参数无效");
        }
        Path file = FileDatasetParseSource.requireLocalFile(source);
        try (OPCPackage packageFile = OPCPackage.open(file.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(packageFile, true);
            boolean use1904Windowing = uses1904DateWindowing(reader);
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            StylesTable styles = reader.getStylesTable();
            return parseSelectedSheet(reader, sharedStrings, styles, spreadsheet, recordLimit, use1904Windowing);
        } catch (FileDatasetParsingException exception) {
            throw exception;
        } catch (POIXMLException | SAXException | OpenXML4JException exception) {
            throw new FileDatasetParsingException("XLSX 文件内容无效", exception);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("无法读取 XLSX 文件", exception);
        }
    }

    private ParseResult parseSelectedSheet(
            XSSFReader reader,
            SharedStrings sharedStrings,
            StylesTable styles,
            FileDatasetParsingConfiguration.Spreadsheet options,
            int recordLimit,
            boolean use1904Windowing
    ) throws IOException, SAXException, OpenXML4JException {
        String requestedSheetName = options.sheetName() == null ? "" : options.sheetName().trim();
        List<String> sheetNames = new ArrayList<>();
        XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();
        while (iterator.hasNext()) {
            try (InputStream sheetStream = iterator.next()) {
                String sheetName = iterator.getSheetName();
                sheetNames.add(sheetName);
                if (!requestedSheetName.isEmpty() && !requestedSheetName.equals(sheetName)) {
                    continue;
                }
                SpreadsheetSampleCollector collector = new SpreadsheetSampleCollector(options, recordLimit);
                XMLReader xmlReader = XMLHelper.newXMLReader();
                xmlReader.setContentHandler(new XlsxSheetHandler(collector, sharedStrings, styles, use1904Windowing));
                xmlReader.parse(new InputSource(sheetStream));
                return collector.result();
            } catch (javax.xml.parsers.ParserConfigurationException exception) {
                throw new FileDatasetParsingException("无法初始化 XLSX 解析器", exception);
            }
        }
        if (sheetNames.isEmpty()) {
            throw new FileDatasetParsingException("XLSX 文件不包含工作表");
        }
        throw new FileDatasetParsingException("指定的工作表不存在：" + requestedSheetName + "；可用工作表："
                + String.join("、", sheetNames.stream().limit(10).toList()));
    }

    private boolean uses1904DateWindowing(XSSFReader reader) throws IOException, SAXException, OpenXML4JException {
        try (InputStream workbookStream = reader.getWorkbookData()) {
            DateWindowingHandler handler = new DateWindowingHandler();
            XMLReader xmlReader = XMLHelper.newXMLReader();
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(workbookStream));
            return handler.use1904Windowing;
        } catch (javax.xml.parsers.ParserConfigurationException exception) {
            throw new FileDatasetParsingException("无法读取 XLSX 工作簿配置", exception);
        }
    }

    private static final class DateWindowingHandler extends DefaultHandler {

        private boolean use1904Windowing;

        @Override
        public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) {
            if ("workbookPr".equals(localName) || "workbookPr".equals(qualifiedName)) {
                String value = attributes.getValue("date1904");
                use1904Windowing = "1".equals(value) || Boolean.parseBoolean(value);
            }
        }
    }

    private static final class XlsxSheetHandler extends DefaultHandler {

        private final SpreadsheetSampleCollector collector;
        private final SharedStrings sharedStrings;
        private final StylesTable styles;
        private final boolean use1904Windowing;

        private final Map<Integer, SpreadsheetCell> currentCells = new LinkedHashMap<>();
        private int currentRowIndex = -1;
        private int previousRowIndex = -1;
        private int currentColumnIndex = -1;
        private int previousColumnIndex = -1;
        private String cellType;
        private int styleIndex;
        private StringBuilder cellValue;
        private StringBuilder formula;
        private CaptureTarget captureTarget = CaptureTarget.NONE;

        private XlsxSheetHandler(
                SpreadsheetSampleCollector collector,
                SharedStrings sharedStrings,
                StylesTable styles,
                boolean use1904Windowing
        ) {
            this.collector = collector;
            this.sharedStrings = sharedStrings;
            this.styles = styles;
            this.use1904Windowing = use1904Windowing;
        }

        @Override
        public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) {
            String name = elementName(localName, qualifiedName);
            if ("row".equals(name)) {
                currentRowIndex = rowIndex(attributes.getValue("r"));
                currentCells.clear();
            } else if ("c".equals(name)) {
                currentColumnIndex = columnIndex(attributes.getValue("r"));
                previousColumnIndex = currentColumnIndex;
                cellType = attributes.getValue("t");
                styleIndex = integerAttribute(attributes.getValue("s"), 0);
                cellValue = null;
                formula = null;
                captureTarget = CaptureTarget.NONE;
            } else if ("v".equals(name)) {
                cellValue = new StringBuilder();
                captureTarget = CaptureTarget.VALUE;
            } else if ("f".equals(name)) {
                formula = new StringBuilder();
                captureTarget = CaptureTarget.FORMULA;
            } else if ("t".equals(name) && "inlineStr".equals(cellType)) {
                if (cellValue == null) {
                    cellValue = new StringBuilder();
                }
                captureTarget = CaptureTarget.VALUE;
            }
        }

        @Override
        public void characters(char[] characters, int start, int length) {
            if (captureTarget == CaptureTarget.VALUE && cellValue != null) {
                cellValue.append(characters, start, length);
            } else if (captureTarget == CaptureTarget.FORMULA && formula != null) {
                formula.append(characters, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qualifiedName) {
            String name = elementName(localName, qualifiedName);
            if ("v".equals(name) || "f".equals(name) || "t".equals(name)) {
                captureTarget = CaptureTarget.NONE;
            } else if ("c".equals(name)) {
                SpreadsheetCell cell = cellValue();
                if (cell != null) {
                    currentCells.put(currentColumnIndex, cell);
                }
            } else if ("row".equals(name)) {
                collector.acceptRow(currentRowIndex, currentCells);
                previousRowIndex = currentRowIndex;
                currentRowIndex = -1;
            }
        }

        private SpreadsheetCell cellValue() {
            String rawValue = cellValue == null ? null : cellValue.toString();
            if (rawValue == null && formula != null) {
                return SpreadsheetValueSupport.text("=" + formula);
            }
            if (rawValue == null) {
                return null;
            }
            try {
                if ("s".equals(cellType)) {
                    return SpreadsheetValueSupport.text(sharedStrings.getItemAt(Integer.parseInt(rawValue)).getString());
                }
                if ("inlineStr".equals(cellType) || "str".equals(cellType)) {
                    return SpreadsheetValueSupport.text(rawValue);
                }
                if ("b".equals(cellType)) {
                    return SpreadsheetValueSupport.booleanValue("1".equals(rawValue) || Boolean.parseBoolean(rawValue));
                }
                if ("e".equals(cellType)) {
                    return new SpreadsheetCell(rawValue, LogicalType.STRING);
                }
                if ("d".equals(cellType)) {
                    return isoDateValue(rawValue);
                }
                return numericValue(rawValue);
            } catch (RuntimeException exception) {
                throw new FileDatasetParsingException("XLSX 单元格内容无效", exception);
            }
        }

        private SpreadsheetCell numericValue(String rawValue) {
            double value = Double.parseDouble(rawValue);
            XSSFCellStyle style = styles.getStyleAt(styleIndex);
            return SpreadsheetValueSupport.numeric(value, style.getDataFormat(), style.getDataFormatString(), use1904Windowing);
        }

        private static SpreadsheetCell isoDateValue(String rawValue) {
            try {
                return new SpreadsheetCell(LocalDate.parse(rawValue), LogicalType.DATE);
            } catch (RuntimeException ignored) {
                try {
                    return new SpreadsheetCell(LocalTime.parse(rawValue), LogicalType.TIME);
                } catch (RuntimeException ignoredAgain) {
                    try {
                        return new SpreadsheetCell(OffsetDateTime.parse(rawValue), LogicalType.DATETIME);
                    } catch (RuntimeException ignoredThird) {
                        return new SpreadsheetCell(LocalDateTime.parse(rawValue), LogicalType.DATETIME);
                    }
                }
            }
        }

        private int rowIndex(String reference) {
            if (reference == null || reference.isBlank()) {
                return previousRowIndex + 1;
            }
            int split = 0;
            while (split < reference.length() && !Character.isDigit(reference.charAt(split))) {
                split++;
            }
            return Integer.parseInt(reference.substring(split)) - 1;
        }

        private int columnIndex(String reference) {
            if (reference == null || reference.isBlank()) {
                return previousColumnIndex + 1;
            }
            int split = 0;
            while (split < reference.length() && Character.isLetter(reference.charAt(split))) {
                split++;
            }
            return CellReference.convertColStringToIndex(reference.substring(0, split));
        }

        private static int integerAttribute(String value, int defaultValue) {
            return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
        }

        private static String elementName(String localName, String qualifiedName) {
            return localName == null || localName.isBlank() ? qualifiedName : localName;
        }

        private enum CaptureTarget {
            NONE,
            VALUE,
            FORMULA
        }
    }
}
