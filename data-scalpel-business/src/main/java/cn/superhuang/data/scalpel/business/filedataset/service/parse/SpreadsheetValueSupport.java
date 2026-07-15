package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/** Normalizes physical spreadsheet cell values into stable API values and logical types. */
final class SpreadsheetValueSupport {

    private SpreadsheetValueSupport() {
    }

    static SpreadsheetCell text(String value) {
        return new SpreadsheetCell(value, FieldCollector.textType(value));
    }

    static SpreadsheetCell booleanValue(boolean value) {
        return new SpreadsheetCell(value, LogicalType.BOOLEAN);
    }

    static SpreadsheetCell error(byte code) {
        try {
            return new SpreadsheetCell(FormulaError.forInt(code).getString(), LogicalType.STRING);
        } catch (IllegalArgumentException exception) {
            return new SpreadsheetCell("#ERROR!", LogicalType.STRING);
        }
    }

    static SpreadsheetCell numeric(double value, int formatIndex, String formatString, boolean use1904Windowing) {
        if (DateUtil.isADateFormat(formatIndex, formatString) && DateUtil.isValidExcelDate(value)) {
            LocalDateTime dateTime = DateUtil.getLocalDateTime(value, use1904Windowing);
            SpreadsheetDateKind kind = dateKind(formatString);
            return switch (kind) {
                case DATE -> new SpreadsheetCell(dateTime.toLocalDate(), LogicalType.DATE);
                case TIME -> new SpreadsheetCell(dateTime.toLocalTime(), LogicalType.TIME);
                case DATETIME -> new SpreadsheetCell(dateTime, LogicalType.DATETIME);
            };
        }
        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        try {
            return decimal.scale() <= 0
                    ? new SpreadsheetCell(decimal.longValueExact(), LogicalType.INTEGER)
                    : new SpreadsheetCell(decimal, LogicalType.DECIMAL);
        } catch (ArithmeticException exception) {
            return new SpreadsheetCell(decimal, LogicalType.DECIMAL);
        }
    }

    private static SpreadsheetDateKind dateKind(String formatString) {
        String format = formatString == null ? "" : formatString.toLowerCase(Locale.ROOT);
        boolean hasDate = format.indexOf('y') >= 0 || format.indexOf('d') >= 0;
        boolean hasTime = format.indexOf('h') >= 0 || format.indexOf('s') >= 0 || format.indexOf(':') >= 0;
        if (hasDate && !hasTime) {
            return SpreadsheetDateKind.DATE;
        }
        if (!hasDate && hasTime) {
            return SpreadsheetDateKind.TIME;
        }
        return SpreadsheetDateKind.DATETIME;
    }

    private enum SpreadsheetDateKind {
        DATE,
        TIME,
        DATETIME
    }
}
