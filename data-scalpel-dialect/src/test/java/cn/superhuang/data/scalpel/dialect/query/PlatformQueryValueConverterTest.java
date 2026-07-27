package cn.superhuang.data.scalpel.dialect.query;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlatformQueryValueConverterTest {

    @Test
    void validatesDecimalIntegerDigitsAndScaleIncludingExponentNotation() {
        PlatformTypeDefinition decimal = PlatformTypeDefinition.decimal(5, 2);

        assertEquals(new BigDecimal("999.99"), PlatformQueryValueConverter.convert("999.99", decimal));
        assertThrows(IllegalArgumentException.class, () ->
                PlatformQueryValueConverter.convert("1000.00", decimal));
        assertThrows(IllegalArgumentException.class, () ->
                PlatformQueryValueConverter.convert("1E+3", decimal));
        assertThrows(IllegalArgumentException.class, () ->
                PlatformQueryValueConverter.convert("1.234", decimal));
    }
}
