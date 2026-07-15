package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import org.apache.poi.hssf.eventusermodel.FormatTrackingHSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFEventFactory;
import org.apache.poi.hssf.eventusermodel.HSSFListener;
import org.apache.poi.hssf.eventusermodel.HSSFRequest;
import org.apache.poi.hssf.record.BOFRecord;
import org.apache.poi.hssf.record.BoolErrRecord;
import org.apache.poi.hssf.record.BoundSheetRecord;
import org.apache.poi.hssf.record.CellValueRecordInterface;
import org.apache.poi.hssf.record.DateWindow1904Record;
import org.apache.poi.hssf.record.ExtendedFormatRecord;
import org.apache.poi.hssf.record.FormatRecord;
import org.apache.poi.hssf.record.FormulaRecord;
import org.apache.poi.hssf.record.LabelRecord;
import org.apache.poi.hssf.record.LabelSSTRecord;
import org.apache.poi.hssf.record.MulRKRecord;
import org.apache.poi.hssf.record.NumberRecord;
import org.apache.poi.hssf.record.RKRecord;
import org.apache.poi.hssf.record.Record;
import org.apache.poi.hssf.record.SSTRecord;
import org.apache.poi.hssf.record.StringRecord;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.BuiltinFormats;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Streams binary XLS records with POI's event API and keeps only the configured sample. */
@Component
public class XlsFileDatasetParser implements FileDatasetParser {

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.XLS;
    }

    @Override
    public FileDatasetParserInputMode inputMode() {
        return FileDatasetParserInputMode.LOCAL_FILE;
    }

    @Override
    public ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.Spreadsheet spreadsheet)) {
            throw new FileDatasetParsingException("XLS 解析参数无效");
        }
        Path file = FileDatasetParseSource.requireLocalFile(source);
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(file.toFile(), true)) {
            XlsSheetListener listener = new XlsSheetListener(spreadsheet, recordLimit);
            FormatTrackingHSSFListener formatTracking = new FormatTrackingHSSFListener(listener);
            listener.setFormatTracking(formatTracking);
            HSSFRequest request = new HSSFRequest();
            request.addListenerForAllRecords(formatTracking);
            new HSSFEventFactory().processWorkbookEvents(request, fileSystem);
            listener.finish();
            return listener.result();
        } catch (FileDatasetParsingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileDatasetParsingException("XLS 文件内容无效", exception);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("无法读取 XLS 文件", exception);
        }
    }

    private static final class XlsSheetListener implements HSSFListener {

        private final FileDatasetParsingConfiguration.Spreadsheet options;
        private final SpreadsheetSampleCollector collector;
        private final List<String> sheetNames = new ArrayList<>();
        private final Map<Integer, SpreadsheetCell> currentCells = new LinkedHashMap<>();
        private final List<ExtendedFormatRecord> extendedFormats = new ArrayList<>();
        private final Map<Integer, String> customFormats = new LinkedHashMap<>();

        private FormatTrackingHSSFListener formatTracking;
        private SSTRecord sharedStrings;
        private int currentSheetIndex = -1;
        private int currentRowIndex = -1;
        private boolean selectedSheet;
        private boolean use1904Windowing;
        private PendingStringFormula pendingStringFormula;

        private XlsSheetListener(FileDatasetParsingConfiguration.Spreadsheet options, int recordLimit) {
            this.options = options;
            this.collector = new SpreadsheetSampleCollector(options, recordLimit);
        }

        void setFormatTracking(FormatTrackingHSSFListener formatTracking) {
            this.formatTracking = formatTracking;
        }

        @Override
        public void processRecord(Record record) {
            if (record instanceof BoundSheetRecord boundSheet) {
                sheetNames.add(boundSheet.getSheetname());
                return;
            }
            if (record instanceof DateWindow1904Record dateWindowing) {
                use1904Windowing = dateWindowing.getWindowing() == 1;
                return;
            }
            if (record instanceof SSTRecord sstRecord) {
                sharedStrings = sstRecord;
                return;
            }
            if (record instanceof ExtendedFormatRecord extendedFormatRecord) {
                extendedFormats.add(extendedFormatRecord);
                return;
            }
            if (record instanceof FormatRecord formatRecord) {
                customFormats.put(formatRecord.getIndexCode(), formatRecord.getFormatString());
                return;
            }
            if (record instanceof BOFRecord bofRecord && bofRecord.getType() == BOFRecord.TYPE_WORKSHEET) {
                finishCurrentRow();
                currentSheetIndex++;
                selectedSheet = isSelectedSheet(currentSheetIndex);
                pendingStringFormula = null;
                return;
            }
            if (!selectedSheet) {
                return;
            }
            if (record instanceof StringRecord stringRecord && pendingStringFormula != null) {
                addCell(pendingStringFormula.rowIndex(), pendingStringFormula.columnIndex(), SpreadsheetValueSupport.text(stringRecord.getString()));
                pendingStringFormula = null;
            } else if (record instanceof LabelSSTRecord labelSstRecord) {
                if (sharedStrings == null) {
                    throw new FileDatasetParsingException("XLS 文件缺少共享字符串表");
                }
                addCell(labelSstRecord.getRow(), labelSstRecord.getColumn(),
                        SpreadsheetValueSupport.text(sharedStrings.getString(labelSstRecord.getSSTIndex()).getString()));
            } else if (record instanceof LabelRecord labelRecord) {
                addCell(labelRecord.getRow(), labelRecord.getColumn(), SpreadsheetValueSupport.text(labelRecord.getValue()));
            } else if (record instanceof NumberRecord numberRecord) {
                addNumericCell(numberRecord, numberRecord.getValue());
            } else if (record instanceof RKRecord rkRecord) {
                addNumericCell(rkRecord, rkRecord.getRKNumber());
            } else if (record instanceof MulRKRecord mulRkRecord) {
                for (int index = 0; index < mulRkRecord.getNumColumns(); index++) {
                    int formatIndex = formatIndex(mulRkRecord.getXFAt(index));
                    String formatString = formatString(formatIndex);
                    addCell(mulRkRecord.getRow(), mulRkRecord.getFirstColumn() + index,
                            SpreadsheetValueSupport.numeric(
                                    mulRkRecord.getRKNumberAt(index), formatIndex, formatString, use1904Windowing
                            ));
                }
            } else if (record instanceof BoolErrRecord boolErrRecord) {
                SpreadsheetCell cell = boolErrRecord.isBoolean()
                        ? SpreadsheetValueSupport.booleanValue(boolErrRecord.getBooleanValue())
                        : SpreadsheetValueSupport.error(boolErrRecord.getErrorValue());
                addCell(boolErrRecord.getRow(), boolErrRecord.getColumn(), cell);
            } else if (record instanceof FormulaRecord formulaRecord) {
                addFormulaCell(formulaRecord);
            }
        }

        void finish() {
            finishCurrentRow();
        }

        ParseResult result() {
            if (currentSheetIndex < 0) {
                throw new FileDatasetParsingException("XLS 文件不包含工作表");
            }
            String requestedSheetName = options.sheetName() == null ? "" : options.sheetName().trim();
            if (!requestedSheetName.isEmpty() && sheetNames.stream().noneMatch(requestedSheetName::equals)) {
                throw new FileDatasetParsingException("指定的工作表不存在：" + requestedSheetName + "；可用工作表："
                        + String.join("、", sheetNames.stream().limit(10).toList()));
            }
            return collector.result();
        }

        private boolean isSelectedSheet(int sheetIndex) {
            String requestedSheetName = options.sheetName() == null ? "" : options.sheetName().trim();
            if (requestedSheetName.isEmpty()) {
                return sheetIndex == 0;
            }
            return sheetIndex < sheetNames.size() && requestedSheetName.equals(sheetNames.get(sheetIndex));
        }

        private void addFormulaCell(FormulaRecord formulaRecord) {
            CellType cachedType = formulaRecord.getCachedResultTypeEnum();
            if (cachedType == CellType.STRING) {
                pendingStringFormula = new PendingStringFormula(formulaRecord.getRow(), formulaRecord.getColumn());
            } else if (cachedType == CellType.BOOLEAN) {
                addCell(formulaRecord.getRow(), formulaRecord.getColumn(),
                        SpreadsheetValueSupport.booleanValue(formulaRecord.getCachedBooleanValue()));
            } else if (cachedType == CellType.ERROR) {
                addCell(formulaRecord.getRow(), formulaRecord.getColumn(),
                        SpreadsheetValueSupport.error((byte) formulaRecord.getCachedErrorValue()));
            } else if (cachedType == CellType.BLANK) {
                addCell(formulaRecord.getRow(), formulaRecord.getColumn(), new SpreadsheetCell(null, null));
            } else {
                addNumericCell(formulaRecord, formulaRecord.getValue());
            }
        }

        private void addNumericCell(CellValueRecordInterface record, double value) {
            int formatIndex = formatTracking.getFormatIndex(record);
            String formatString = formatTracking.getFormatString(record);
            addCell(record.getRow(), record.getColumn(),
                    SpreadsheetValueSupport.numeric(value, formatIndex, formatString, use1904Windowing));
        }

        private int formatIndex(short extendedFormatIndex) {
            int index = Short.toUnsignedInt(extendedFormatIndex);
            return index < extendedFormats.size() ? Short.toUnsignedInt(extendedFormats.get(index).getFormatIndex()) : 0;
        }

        private String formatString(int formatIndex) {
            return customFormats.getOrDefault(formatIndex, BuiltinFormats.getBuiltinFormat(formatIndex));
        }

        private void addCell(int rowIndex, int columnIndex, SpreadsheetCell cell) {
            if (currentRowIndex >= 0 && currentRowIndex != rowIndex) {
                finishCurrentRow();
            }
            currentRowIndex = rowIndex;
            currentCells.put(columnIndex, cell);
        }

        private void finishCurrentRow() {
            if (selectedSheet && currentRowIndex >= 0) {
                collector.acceptRow(currentRowIndex, currentCells);
            }
            currentCells.clear();
            currentRowIndex = -1;
        }

        private record PendingStringFormula(int rowIndex, int columnIndex) {
        }
    }
}
