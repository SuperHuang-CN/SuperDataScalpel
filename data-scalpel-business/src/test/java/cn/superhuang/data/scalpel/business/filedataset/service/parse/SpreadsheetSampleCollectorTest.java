package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpreadsheetSampleCollectorTest {

    @Test
    void countsAllRowsWhileRetainingOnlyThePreview() {
        var collector = collector(0, 1, 1000);
        collector.acceptRow(0, text("inspection_id"));
        for (int row = 1; row <= 100_000; row++) {
            collector.acceptRow(row, number(row));
        }

        var result = collector.result();
        assertEquals(100_000, result.rowCount());
        assertEquals(1000, result.rows().size());
        assertEquals(1L, result.rows().getFirst().get("inspection_id"));
        assertEquals(1000L, result.rows().getLast().get("inspection_id"));
        assertTrue(result.truncated());
    }

    @Test
    void excludesHeaderPreambleAndEmptyRowsWithoutCountingRowNumberGaps() {
        var collector = collector(2, 4, 1);
        collector.acceptRow(0, text("preamble"));
        collector.acceptRow(2, text("id"));
        collector.acceptRow(3, text("instructions"));
        collector.acceptRow(4, Map.of());
        collector.acceptRow(5, Map.of(0, new SpreadsheetCell(null, null)));
        collector.acceptRow(6, text(" \t "));
        collector.acceptRow(7, number(0));
        collector.acceptRow(100, number(2));
        collector.acceptRow(101, text(" "));

        var result = collector.result();
        assertEquals(2, result.rowCount());
        assertEquals(1, result.rows().size());
        assertEquals(0L, result.rows().getFirst().get("id"));
        assertTrue(result.truncated());
    }

    @Test
    void doesNotMarkAnExactLimitAsTruncatedAndCountsFalseAsData() {
        var collector = collector(0, 1, 2);
        collector.acceptRow(0, text("enabled"));
        collector.acceptRow(1, Map.of(0, new SpreadsheetCell(false, LogicalType.BOOLEAN)));
        collector.acceptRow(2, Map.of(0, new SpreadsheetCell(true, LogicalType.BOOLEAN)));
        collector.acceptRow(3, Map.of());

        var result = collector.result();
        assertEquals(2, result.rowCount());
        assertEquals(2, result.rows().size());
        assertFalse(result.truncated());
    }

    @Test
    void headerOnlySheetHasNoDataRows() {
        var collector = collector(0, 1, 1000);
        collector.acceptRow(0, text("id"));

        var result = collector.result();
        assertEquals(0, result.rowCount());
        assertTrue(result.rows().isEmpty());
        assertEquals(1, result.fields().size());
        assertFalse(result.truncated());
    }

    private static SpreadsheetSampleCollector collector(int header, int start, int limit) {
        return new SpreadsheetSampleCollector(
                new FileDatasetParsingConfiguration.Spreadsheet("sheet", header, start), limit);
    }

    private static Map<Integer, SpreadsheetCell> text(String value) {
        return Map.of(0, new SpreadsheetCell(value, LogicalType.STRING));
    }

    private static Map<Integer, SpreadsheetCell> number(long value) {
        return Map.of(0, new SpreadsheetCell(value, LogicalType.INTEGER));
    }
}
