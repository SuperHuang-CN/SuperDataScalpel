package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldCollectorTest {
    @Test
    void decimalSchemaCombinesMaximumIntegerDigitsAndMaximumScaleAcrossRows() {
        FieldCollector collector = new FieldCollector(1);
        for (String value : new String[]{"9.99", "10.0", "999", "0.0001"}) {
            collector.addRow(Map.of("amount", new BigDecimal(value)), Map.of("amount", LogicalType.DECIMAL));
        }
        var type = collector.fields().getFirst().type();
        assertEquals(7, type.precision());
        assertEquals(4, type.scale());
        assertEquals(1, collector.rows().size());
        assertEquals(4, collector.rowCount());
    }
}
