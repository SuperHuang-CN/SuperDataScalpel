package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OracleFloatingTypeTest {
    @Test
    void importsOracleIeeeFloatsWithVendorJdbcCodesWithoutChangingPrecision() {
        var dialect = new OracleDialect();
        for (int code : new int[]{100, 101}) {
            var column = new ColumnMetadata("reading", 1, code,
                    code == 100 ? "BINARY_FLOAT" : "BINARY_DOUBLE", LogicalType.OTHER,
                    null, null, null, true, null, false, false, null);
            assertEquals(code == 100 ? PlatformDataType.FLOAT : PlatformDataType.DOUBLE,
                    dialect.mapToPlatformType(JdbcTypeDescriptor.from(column)).definition().type());
        }
    }
}
