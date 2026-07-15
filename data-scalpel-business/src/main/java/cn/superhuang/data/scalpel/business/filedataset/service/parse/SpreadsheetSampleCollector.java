package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies spreadsheet row settings and builds one bounded, rectangular sample. */
final class SpreadsheetSampleCollector {

    private final int headerRowIndex;
    private final int dataStartRowIndex;
    private final int recordLimit;
    private final List<Map<Integer, SpreadsheetCell>> dataRows = new ArrayList<>();

    private Map<Integer, SpreadsheetCell> headerCells = Map.of();
    private int maximumColumnIndex = -1;
    private boolean truncated;

    SpreadsheetSampleCollector(FileDatasetParsingConfiguration.Spreadsheet options, int recordLimit) {
        if (recordLimit < 1) {
            throw new IllegalArgumentException("抽样记录数必须大于零");
        }
        this.headerRowIndex = options.headerRowIndex();
        this.dataStartRowIndex = options.dataStartRowIndex();
        this.recordLimit = recordLimit;
    }

    void acceptRow(int rowIndex, Map<Integer, SpreadsheetCell> cells) {
        if (rowIndex == headerRowIndex) {
            headerCells = copy(cells);
            updateMaximumColumn(cells);
            return;
        }
        if (rowIndex < dataStartRowIndex || cells.values().stream().allMatch(SpreadsheetCell::isEmpty)) {
            return;
        }
        updateMaximumColumn(cells);
        if (dataRows.size() >= recordLimit) {
            truncated = true;
            return;
        }
        dataRows.add(copy(cells));
    }

    FileDatasetParser.ParseResult result() {
        if (maximumColumnIndex < 0) {
            throw new FileDatasetParsingException("Excel 文件不包含字段");
        }
        List<String> columnNames = columnNames(maximumColumnIndex + 1);
        FieldCollector collector = new FieldCollector();
        columnNames.forEach(collector::ensureField);
        for (Map<Integer, SpreadsheetCell> cells : dataRows) {
            Map<String, Object> values = new LinkedHashMap<>();
            Map<String, LogicalType> types = new LinkedHashMap<>();
            for (int columnIndex = 0; columnIndex < columnNames.size(); columnIndex++) {
                SpreadsheetCell cell = cells.get(columnIndex);
                String columnName = columnNames.get(columnIndex);
                values.put(columnName, cell == null ? null : cell.value());
                types.put(columnName, cell == null ? null : cell.logicalType());
            }
            collector.addRow(values, types);
        }
        return new FileDatasetParser.ParseResult(collector.fields(), collector.rows(), truncated);
    }

    private void updateMaximumColumn(Map<Integer, SpreadsheetCell> cells) {
        cells.keySet().stream().mapToInt(Integer::intValue).max()
                .ifPresent(index -> maximumColumnIndex = Math.max(maximumColumnIndex, index));
    }

    private List<String> columnNames(int columnCount) {
        Set<String> names = new LinkedHashSet<>();
        List<String> values = new ArrayList<>(columnCount);
        for (int index = 0; index < columnCount; index++) {
            SpreadsheetCell header = headerCells.get(index);
            String preferred = header == null || header.value() == null ? "" : String.valueOf(header.value()).trim();
            if (preferred.isBlank()) {
                preferred = "column_" + (index + 1);
            }
            String name = preferred;
            int suffix = 2;
            while (!names.add(name)) {
                name = preferred + "_" + suffix++;
            }
            values.add(name);
        }
        return values;
    }

    private static Map<Integer, SpreadsheetCell> copy(Map<Integer, SpreadsheetCell> cells) {
        return Map.copyOf(new LinkedHashMap<>(cells));
    }
}
